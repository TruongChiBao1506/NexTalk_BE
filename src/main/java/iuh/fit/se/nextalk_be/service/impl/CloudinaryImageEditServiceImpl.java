package iuh.fit.se.nextalk_be.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import iuh.fit.se.nextalk_be.dto.request.ImageEditOperation;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "app.image-ai", name = "provider", havingValue = "cloudinary")
@RequiredArgsConstructor
public class CloudinaryImageEditServiceImpl implements ImageEditService {
    private static final long MAX_RESULT_BYTES = 15L * 1024 * 1024;
    private static final Set<String> ALLOWED_ASPECT_RATIOS =
            Set.of("1:1", "16:9", "9:16", "4:3", "3:4");
    private static final int MAX_ATTEMPTS = 8;

    private final MessageRepository messageRepository;
    private final MediaAuthorizationService mediaAuthorizationService;
    private final CloudinaryService cloudinaryService;
    private final RestTemplate restTemplate;
    private final Cloudinary cloudinary;

    @Value("${app.image-ai.enabled:false}")
    private boolean enabled;

    @Override
    public ImageEditResponse edit(ImageEditRequest request) {
        if (!enabled) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Tính năng chỉnh sửa ảnh AI đang tắt.");
        }

        Message message = messageRepository.findById(request.getMessageId())
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (message.isRecalled() || !isImageInMessage(message, request.getSourceUrl())) {
            throw new BadRequestException("The selected image is not available in this message");
        }
        mediaAuthorizationService.assertCanDownload(request.getSourceUrl());

        CloudinaryAsset asset = parseSource(request.getSourceUrl());
        String transformation = buildTransformation(request);
        String transformedUrl = cloudinary.url()
                .resourceType("image")
                .type("upload")
                .secure(true)
                .signed(true)
                .version(asset.version())
                .format(asset.format())
                .transformation(new Transformation<>().rawTransformation(transformation))
                .generate(asset.publicId());

        GeneratedImage generated = downloadWhenReady(URI.create(transformedUrl));
        try {
            String extension = extensionFor(generated.contentType());
            FileUploadResponse upload = cloudinaryService.uploadGeneratedImage(
                    generated.bytes(),
                    generated.contentType(),
                    "nextalk-cloudinary-" + request.getOperation().name().toLowerCase(Locale.ROOT)
                            + "-" + System.currentTimeMillis() + extension
            );
            mediaAuthorizationService.claimUpload(upload.getUrl());
            return ImageEditResponse.builder()
                    .sourceUrl(request.getSourceUrl())
                    .url(upload.getUrl())
                    .publicId(upload.getPublicId())
                    .fileName(upload.getFileName())
                    .contentType(upload.getContentType())
                    .size(upload.getSize())
                    .model("cloudinary/" + request.getOperation().name().toLowerCase(Locale.ROOT))
                    .build();
        } catch (IOException exception) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh đã chỉnh sửa.");
        }
    }

    String buildTransformation(ImageEditRequest request) {
        if (request.getOperation() == null) {
            throw new BadRequestException("Vui lòng chọn thao tác chỉnh sửa.");
        }
        return switch (request.getOperation()) {
            case REMOVE -> "e_gen_remove:prompt_" + requiredText(request.getSubject(), "Vui lòng nhập vật thể cần xóa")
                    + ";multiple_true;remove-shadow_true";
            case REPLACE -> "e_gen_replace:from_" + requiredText(request.getSubject(), "Vui lòng nhập vật thể cần thay")
                    + ";to_" + requiredText(request.getReplacement(), "Vui lòng nhập vật thể thay thế")
                    + ";preserve-geometry_true";
            case RECOLOR -> "e_gen_recolor:prompt_(" + requiredText(request.getSubject(), "Vui lòng nhập vật thể cần đổi màu")
                    + ");to-color_" + validColor(request.getColor());
            case BACKGROUND_REPLACE -> "e_gen_background_replace:prompt_"
                    + requiredText(request.getPrompt(), "Vui lòng mô tả nền mới");
            case FILL -> "ar_" + validAspectRatio(request.getAspectRatio())
                    + ",b_gen_fill" + optionalPrompt(request.getPrompt()) + ",c_pad";
            case RESTORE -> "e_gen_restore";
        };
    }

    private String requiredText(String value, String message) {
        if (value == null || value.trim().length() < 2) {
            throw new BadRequestException(message);
        }
        String safe = value.trim()
                .replaceAll("[^\\p{L}\\p{N}\\s-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (safe.length() > 200) {
            throw new BadRequestException("Mô tả không được vượt quá 200 ký tự.");
        }
        return safe.replace(" ", "%20");
    }

    private String optionalPrompt(String value) {
        return value == null || value.isBlank() ? "" : ":prompt_" + requiredText(value, "");
    }

    private String validColor(String value) {
        if (value == null || !value.matches("#?[0-9a-fA-F]{6}")) {
            throw new BadRequestException("Màu phải có định dạng HEX, ví dụ #4F46E5.");
        }
        return value.replace("#", "").toLowerCase(Locale.ROOT);
    }

    private String validAspectRatio(String value) {
        if (value == null || !ALLOWED_ASPECT_RATIOS.contains(value)) {
            throw new BadRequestException("Tỷ lệ ảnh không được hỗ trợ.");
        }
        return value;
    }

    private CloudinaryAsset parseSource(String sourceUrl) {
        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid source image URL");
        }
        String expectedPrefix = "/" + cloudinary.config.cloudName + "/image/upload/";
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                || !uri.getPath().startsWith(expectedPrefix)) {
            throw new BadRequestException("Only NexTalk Cloudinary images can be edited");
        }

        String remainder = uri.getPath().substring(expectedPrefix.length());
        String version = null;
        String assetPath = remainder;
        String[] segments = remainder.split("/");
        for (int index = 0; index < segments.length; index++) {
            if (segments[index].matches("v\\d+")) {
                version = segments[index].substring(1);
                assetPath = String.join("/", java.util.Arrays.copyOfRange(segments, index + 1, segments.length));
                break;
            }
        }
        int dot = assetPath.lastIndexOf('.');
        if (dot <= assetPath.lastIndexOf('/') || dot == assetPath.length() - 1) {
            throw new BadRequestException("Cloudinary source image format is missing");
        }
        String format = assetPath.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!Set.of("jpg", "jpeg", "png", "webp").contains(format)) {
            throw new BadRequestException("Cloudinary AI chỉ hỗ trợ ảnh JPG, PNG hoặc WebP.");
        }
        return new CloudinaryAsset(assetPath.substring(0, dot), format, version);
    }

    private GeneratedImage downloadWhenReady(URI uri) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<byte[]> response = restTemplate.getForEntity(uri, byte[].class);
                byte[] bytes = response.getBody();
                if (!response.getStatusCode().is2xxSuccessful() || bytes == null || bytes.length == 0) {
                    throw new AppException(HttpStatus.BAD_GATEWAY, "Cloudinary không trả về ảnh đã chỉnh sửa.");
                }
                if (bytes.length > MAX_RESULT_BYTES) {
                    throw new BadRequestException("Ảnh kết quả vượt quá 15 MB.");
                }
                MediaType mediaType = response.getHeaders().getContentType();
                String contentType = mediaType == null ? contentTypeFromPath(uri.getPath()) : mediaType.toString();
                if (!contentType.startsWith("image/")) {
                    throw new AppException(HttpStatus.BAD_GATEWAY, "Phản hồi từ Cloudinary không phải là ảnh.");
                }
                return new GeneratedImage(bytes, contentType);
            } catch (HttpStatusCodeException exception) {
                int status = exception.getStatusCode().value();
                if ((status == 420 || status == 423) && attempt < MAX_ATTEMPTS) {
                    waitForGeneration(attempt);
                    continue;
                }
                throw mapCloudinaryError(status);
            } catch (RestClientException exception) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "Không thể kết nối đến Cloudinary.");
            }
        }
        throw new AppException(HttpStatus.GATEWAY_TIMEOUT, "Cloudinary cần thêm thời gian để tạo ảnh. Vui lòng thử lại.");
    }

    private void waitForGeneration(int attempt) {
        try {
            Thread.sleep(Math.min(3_000L, 500L + attempt * 500L));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Yêu cầu chỉnh sửa ảnh đã bị gián đoạn.");
        }
    }

    private AppException mapCloudinaryError(int status) {
        return switch (status) {
            case 400, 404 -> new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Cloudinary không thể áp dụng thao tác này cho ảnh. Hãy thử mô tả hoặc ảnh khác."
            );
            case 401, 403 -> new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudinary chưa được cấp quyền tạo biến thể AI."
            );
            case 420, 423 -> new AppException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Cloudinary vẫn đang tạo ảnh. Vui lòng thử lại sau ít phút."
            );
            case 429 -> new AppException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Cloudinary đã đạt giới hạn sử dụng hiện tại. Vui lòng thử lại sau."
            );
            default -> new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudinary không thể chỉnh sửa ảnh lúc này."
            );
        };
    }

    private boolean isImageInMessage(Message message, String sourceUrl) {
        boolean attachmentMatch = message.getAttachments() != null && message.getAttachments().stream()
                .anyMatch(attachment -> attachment != null
                        && "IMAGE".equalsIgnoreCase(attachment.getType())
                        && sourceUrl.equals(attachment.getUrl()));
        return attachmentMatch || (message.getMessageType() == MessageType.IMAGE
                && sourceUrl.equals(message.getContent()));
    }

    private String contentTypeFromPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private record CloudinaryAsset(String publicId, String format, String version) {}

    private record GeneratedImage(byte[] bytes, String contentType) {}
}
