package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.ImageEditRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.ImageEditResponse;
import iuh.fit.se.nextalk_be.service.ImageEditService;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Tag(name = "AI Image Editing", description = "Edit accessible chat images with the configured AI provider")
public class ImageEditController {
    private final ImageEditService imageEditService;
    private final RateLimitService rateLimitService;

    @Value("${app.rate-limit.image-edit.limit:10}")
    private int imageEditRateLimit;

    @Value("${app.rate-limit.image-edit.window-seconds:3600}")
    private long imageEditRateWindowSeconds;

    @PostMapping("/edit")
    @Operation(summary = "Create an AI-edited copy of an image attachment")
    public ResponseEntity<ApiResponse<ImageEditResponse>> edit(@Valid @RequestBody ImageEditRequest request) {
        rateLimitService.check(
                "image-ai:edit:v2",
                rateLimitService.currentUserIdentity(),
                imageEditRateLimit,
                Duration.ofSeconds(imageEditRateWindowSeconds),
                "Bạn đã gửi quá nhiều yêu cầu chỉnh sửa ảnh. Vui lòng chờ rồi thử lại."
        );
        return ResponseEntity.ok(ApiResponse.success(
                imageEditService.edit(request),
                "Image edited successfully"
        ));
    }
}
