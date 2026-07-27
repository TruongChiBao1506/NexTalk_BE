package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.ImageEditRequest;
import iuh.fit.se.nextalk_be.dto.response.FileUploadResponse;
import iuh.fit.se.nextalk_be.dto.response.ImageEditResponse;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.exception.AppException;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.service.CloudinaryService;
import iuh.fit.se.nextalk_be.service.ImageEditService;
import iuh.fit.se.nextalk_be.service.MediaAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Map;

@Service
@ConditionalOnProperty(
        prefix = "app.image-ai",
        name = "provider",
        havingValue = "cloudflare"
)
@RequiredArgsConstructor
public class CloudflareImageEditServiceImpl implements ImageEditService {
    private static final long MAX_SOURCE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_CLOUDFLARE_INPUT_SIDE = 511;
    private static final int MAX_OUTPUT_SIDE = 1024;
    private static final int MIN_OUTPUT_SIDE = 256;

    private final MessageRepository messageRepository;
    private final MediaAuthorizationService mediaAuthorizationService;
    private final CloudinaryService cloudinaryService;
    private final RestTemplate restTemplate;

    @Value("${app.image-ai.enabled:false}")
    private boolean enabled;

    @Value("${app.image-ai.cloudflare-account-id:}")
    private String accountId;

    @Value("${app.image-ai.cloudflare-api-token:}")
    private String apiToken;

    @Value("${app.image-ai.cloudflare-model:@cf/black-forest-labs/flux-2-klein-4b}")
    private String model;

    @Value("${app.image-ai.cloudflare-url:https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/run/{model}}")
    private String cloudflareUrl;

    @Override
    public ImageEditResponse edit(ImageEditRequest request) {
        assertConfigured();

        Message message = messageRepository.findById(request.getMessageId())
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (message.isRecalled() || !isImageInMessage(message, request.getSourceUrl())) {
            throw new BadRequestException("The selected image is not available in this message");
        }
        mediaAuthorizationService.assertCanDownload(request.getSourceUrl());

        SourceImage source = downloadSource(request.getSourceUrl());
        PreparedImage prepared = prepareForCloudflare(source.bytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken.trim());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("prompt", buildPrompt(ImageEditInstruction.from(request)));
        form.add("width", Integer.toString(prepared.outputWidth()));
        form.add("height", Integer.toString(prepared.outputHeight()));
        form.add("input_image_0", new NamedByteArrayResource(prepared.bytes(), "source.png"));

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    buildEndpoint(),
                    new HttpEntity<>(form, headers),
                    Map.class
            );
            GeneratedImage generated = extractGeneratedImage(response.getBody());
            FileUploadResponse upload = cloudinaryService.uploadGeneratedImage(
                    generated.bytes(),
                    generated.contentType(),
                    "nextalk-ai-edit-" + System.currentTimeMillis() + ".png"
            );
            mediaAuthorizationService.claimUpload(upload.getUrl());
            return ImageEditResponse.builder()
                    .sourceUrl(request.getSourceUrl())
                    .url(upload.getUrl())
                    .publicId(upload.getPublicId())
                    .fileName(upload.getFileName())
                    .contentType(upload.getContentType())
                    .size(upload.getSize())
                    .model(model)
                    .build();
        } catch (HttpStatusCodeException exception) {
            throw mapCloudflareError(exception);
        } catch (RestClientException exception) {
            throw new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể kết nối đến Cloudflare Workers AI. Vui lòng thử lại sau."
            );
        } catch (IOException exception) {
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể lưu ảnh đã chỉnh sửa."
            );
        }
    }

    private void assertConfigured() {
        if (!enabled) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Tính năng chỉnh sửa ảnh AI đang tắt.");
        }
        if (accountId == null || accountId.isBlank() || apiToken == null || apiToken.isBlank()) {
            throw new AppException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloudflare Workers AI chưa được cấu hình. Cần CLOUDFLARE_ACCOUNT_ID và CLOUDFLARE_AI_TOKEN."
            );
        }
    }

    private boolean isImageInMessage(Message message, String sourceUrl) {
        boolean attachmentMatch = message.getAttachments() != null && message.getAttachments().stream()
                .anyMatch(attachment -> attachment != null
                        && "IMAGE".equalsIgnoreCase(attachment.getType())
                        && sourceUrl.equals(attachment.getUrl()));
        return attachmentMatch || (message.getMessageType() == MessageType.IMAGE
                && sourceUrl.equals(message.getContent()));
    }

    private SourceImage downloadSource(String sourceUrl) {
        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid source image URL");
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || host == null
                || !(host.equals("res.cloudinary.com") || host.endsWith(".res.cloudinary.com"))) {
            throw new BadRequestException("Only NexTalk Cloudinary images can be edited");
        }

        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(uri, byte[].class);
            byte[] bytes = response.getBody();
            MediaType mediaType = response.getHeaders().getContentType();
            if (!response.getStatusCode().is2xxSuccessful() || bytes == null || bytes.length == 0) {
                throw new BadRequestException("Could not read the source image");
            }
            if (bytes.length > MAX_SOURCE_BYTES) {
                throw new BadRequestException("Source image must not exceed 10 MB");
            }
            String contentType = mediaType == null ? "image/jpeg" : mediaType.toString();
            if (!contentType.startsWith("image/")) {
                throw new BadRequestException("The selected attachment is not an image");
            }
            return new SourceImage(bytes);
        } catch (HttpStatusCodeException exception) {
            throw new BadRequestException("Could not read the source image");
        } catch (RestClientException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "Không thể tải ảnh nguồn từ Cloudinary.");
        }
    }

    private PreparedImage prepareForCloudflare(byte[] sourceBytes) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
                throw new BadRequestException("Cloudflare chỉ hỗ trợ ảnh nguồn JPG, PNG hoặc GIF hợp lệ.");
            }

            double inputScale = Math.min(
                    1.0,
                    Math.min(
                            (double) MAX_CLOUDFLARE_INPUT_SIDE / source.getWidth(),
                            (double) MAX_CLOUDFLARE_INPUT_SIDE / source.getHeight()
                    )
            );
            int inputWidth = Math.max(1, (int) Math.floor(source.getWidth() * inputScale));
            int inputHeight = Math.max(1, (int) Math.floor(source.getHeight() * inputScale));

            BufferedImage resized = new BufferedImage(inputWidth, inputHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = resized.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, inputWidth, inputHeight, null);
            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(resized, "png", output)) {
                throw new BadRequestException("Không thể chuyển đổi ảnh nguồn sang PNG.");
            }

            OutputSize outputSize = calculateOutputSize(source.getWidth(), source.getHeight());
            return new PreparedImage(output.toByteArray(), outputSize.width(), outputSize.height());
        } catch (IOException exception) {
            throw new BadRequestException("Không thể đọc ảnh nguồn. Vui lòng thử ảnh JPG hoặc PNG khác.");
        }
    }

    private OutputSize calculateOutputSize(int sourceWidth, int sourceHeight) {
        double scale = Math.min(
                (double) MAX_OUTPUT_SIDE / sourceWidth,
                (double) MAX_OUTPUT_SIDE / sourceHeight
        );
        int width = Math.max(MIN_OUTPUT_SIDE, (int) Math.round(sourceWidth * scale));
        int height = Math.max(MIN_OUTPUT_SIDE, (int) Math.round(sourceHeight * scale));
        return new OutputSize(Math.min(MAX_OUTPUT_SIDE, width), Math.min(MAX_OUTPUT_SIDE, height));
    }

    private URI buildEndpoint() {
        String endpoint = cloudflareUrl
                .replace("{accountId}", accountId.trim())
                .replace("{model}", model.trim());
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Cloudflare endpoint must use HTTPS");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Cloudflare Workers AI URL không hợp lệ.");
        }
    }

    private GeneratedImage extractGeneratedImage(Map<?, ?> body) {
        if (body == null || Boolean.FALSE.equals(body.get("success"))) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "Cloudflare không trả về ảnh đã chỉnh sửa.");
        }
        Object resultValue = body.get("result");
        if (!(resultValue instanceof Map<?, ?> result) || !(result.get("image") instanceof String encoded)) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "Phản hồi ảnh từ Cloudflare không hợp lệ.");
        }
        int commaIndex = encoded.indexOf(',');
        String base64 = encoded.startsWith("data:") && commaIndex >= 0
                ? encoded.substring(commaIndex + 1)
                : encoded;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length == 0) {
                throw new IllegalArgumentException("Empty image");
            }
            return new GeneratedImage(bytes, "image/png");
        } catch (IllegalArgumentException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "Dữ liệu ảnh Cloudflare trả về không hợp lệ.");
        }
    }

    private String buildPrompt(String userPrompt) {
        return "Edit input_image_0 according to this instruction: " + userPrompt.trim()
                + ". Preserve unrelated subjects, identity, composition and visual quality.";
    }

    private AppException mapCloudflareError(HttpStatusCodeException exception) {
        return switch (exception.getStatusCode().value()) {
            case 400 -> new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Cloudflare không thể xử lý ảnh hoặc yêu cầu này. Vui lòng thử ảnh JPG/PNG hoặc mô tả khác."
            );
            case 401, 403 -> new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudflare API Token hoặc quyền Workers AI chưa hợp lệ."
            );
            case 404 -> new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "Không tìm thấy tài khoản hoặc model Cloudflare Workers AI đã cấu hình."
            );
            case 429 -> new AppException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Cloudflare Workers AI đã đạt giới hạn miễn phí hôm nay. Vui lòng thử lại sau."
            );
            default -> new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudflare Workers AI không thể chỉnh sửa ảnh lúc này. Vui lòng thử lại sau."
            );
        };
    }

    private record SourceImage(byte[] bytes) {}

    private record PreparedImage(byte[] bytes, int outputWidth, int outputHeight) {}

    private record OutputSize(int width, int height) {}

    private record GeneratedImage(byte[] bytes, String contentType) {}

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
