package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.FileUploadResponse;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.CloudinaryService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "APIs for uploading files")
public class FileController {

    private final CloudinaryService cloudinaryService;
    private final RateLimitService rateLimitService;

    @PostMapping("/upload")
    @Operation(summary = "Upload a file to Cloudinary")
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(@RequestParam("file") MultipartFile file) {
        rateLimitService.check("file:upload", rateLimitService.currentUserIdentity(), 30, Duration.ofMinutes(10));
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File is empty", null));
        }

        try {
            Map uploadResult = cloudinaryService.uploadFile(file);
            String url = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");

            FileUploadResponse response = FileUploadResponse.builder()
                    .url(url)
                    .publicId(publicId)
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response, "File uploaded successfully"));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to upload file: " + e.getMessage(), null));
        }
    }
}
