package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.ImageEditRequest;
import iuh.fit.se.nextalk_be.dto.response.FileUploadResponse;
import iuh.fit.se.nextalk_be.dto.response.ImageEditResponse;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.AppException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.service.CloudinaryService;
import iuh.fit.se.nextalk_be.service.ImageEditService;
import iuh.fit.se.nextalk_be.service.MediaAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(
        prefix = "app.image-ai",
        name = "provider",
        havingValue = "gemini",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class GeminiImageEditServiceImpl implements ImageEditService {
    private static final long MAX_SOURCE_BYTES = 10L * 1024 * 1024;

    private final MessageRepository messageRepository;
    private final MediaAuthorizationService mediaAuthorizationService;
    private final CloudinaryService cloudinaryService;
    private final RestTemplate restTemplate;

    @Value("${app.image-ai.enabled:false}")
    private boolean enabled;

    @Value("${app.image-ai.gemini-api-key:}")
    private String apiKey;

    @Value("${app.image-ai.gemini-model:gemini-2.5-flash-image}")
    private String model;

    @Value("${app.image-ai.gemini-url:https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}}")
    private String geminiUrl;

    @Override
    public ImageEditResponse edit(ImageEditRequest request) {
        if (!enabled) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Tính năng chỉnh sửa ảnh AI đang tắt.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new AppException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini chưa được cấu hình. Cần biến môi trường GEMINI_API_KEY."
            );
        }

        Message message = messageRepository.findById(request.getMessageId())
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (message.isRecalled() || !isImageInMessage(message, request.getSourceUrl())) {
            throw new BadRequestException("The selected image is not available in this message");
        }
        mediaAuthorizationService.assertCanDownload(request.getSourceUrl());

        SourceImage source = downloadSource(request.getSourceUrl());
        Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(
                                Map.of("text", buildPrompt(ImageEditInstruction.from(request))),
                                Map.of("inlineData", Map.of(
                                        "mimeType", source.contentType(),
                                        "data", Base64.getEncoder().encodeToString(source.bytes())
                                ))
                        )
                )),
                "generationConfig", Map.of("responseModalities", List.of("TEXT", "IMAGE"))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    geminiUrl,
                    new HttpEntity<>(payload, headers),
                    Map.class,
                    model,
                    apiKey
            );
            GeneratedImage generated = extractGeneratedImage(response.getBody());
            String extension = extensionFor(generated.contentType());
            FileUploadResponse upload = cloudinaryService.uploadGeneratedImage(
                    generated.bytes(),
                    generated.contentType(),
                    "nextalk-ai-edit-" + System.currentTimeMillis() + extension
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
            throw mapGeminiError(exception);
        } catch (RestClientException exception) {
            throw new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể kết nối đến dịch vụ chỉnh sửa ảnh Gemini. Vui lòng thử lại sau."
            );
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("The edited image could not be stored", exception);
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
        return new SourceImage(bytes, contentType);
    }

    private GeneratedImage extractGeneratedImage(Map<?, ?> body) {
        if (body == null) throw new IllegalStateException("Gemini returned an empty response");
        Object candidatesValue = body.get("candidates");
        if (!(candidatesValue instanceof List<?> candidates) || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini did not return an edited image");
        }
        Object first = candidates.get(0);
        if (!(first instanceof Map<?, ?> candidate)
                || !(candidate.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts)) {
            throw new IllegalStateException("Gemini returned an invalid image response");
        }
        for (Object partValue : parts) {
            if (!(partValue instanceof Map<?, ?> part)) continue;
            Object inlineValue = part.get("inlineData");
            if (!(inlineValue instanceof Map<?, ?>)) inlineValue = part.get("inline_data");
            if (!(inlineValue instanceof Map<?, ?> inline)) continue;
            Object data = inline.get("data");
            Object mimeType = inline.containsKey("mimeType") ? inline.get("mimeType") : inline.get("mime_type");
            if (data instanceof String encoded && mimeType instanceof String type && type.startsWith("image/")) {
                return new GeneratedImage(Base64.getDecoder().decode(encoded), type);
            }
        }
        throw new IllegalStateException("Gemini did not return an edited image");
    }

    private String buildPrompt(String userPrompt) {
        return "Edit the provided image according to this instruction: " + userPrompt.trim()
                + ". Preserve unrelated subjects and composition. Return the edited image.";
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private AppException mapGeminiError(HttpStatusCodeException exception) {
        int status = exception.getStatusCode().value();
        if (status == 429) {
            return new AppException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Gemini Image không còn quota khả dụng cho API key này. "
                            + "Vui lòng kiểm tra quota hoặc bật billing cho Google AI project."
            );
        }
        if (status == 400) {
            return new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Gemini không thể xử lý ảnh hoặc yêu cầu chỉnh sửa này. Vui lòng thử ảnh hoặc mô tả khác."
            );
        }
        if (status == 403) {
            return new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "API key chưa được cấp quyền sử dụng Gemini Image. Vui lòng kiểm tra project và billing."
            );
        }
        if (status == 404) {
            return new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "Model Gemini Image đã cấu hình hiện không khả dụng."
            );
        }
        return new AppException(
                HttpStatus.BAD_GATEWAY,
                "Gemini không thể chỉnh sửa ảnh lúc này. Vui lòng thử lại sau."
        );
    }

    private record SourceImage(byte[] bytes, String contentType) {}
    private record GeneratedImage(byte[] bytes, String contentType) {}
}
