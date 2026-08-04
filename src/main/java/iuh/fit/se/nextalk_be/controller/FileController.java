package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.FileUploadResponse;
import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.CloudinaryService;
import iuh.fit.se.nextalk_be.service.MediaAuthorizationService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import iuh.fit.se.nextalk_be.dto.request.DirectUploadConfirmRequest;
import iuh.fit.se.nextalk_be.dto.request.DirectUploadPrepareRequest;
import iuh.fit.se.nextalk_be.dto.response.DirectUploadPrepareResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.InputStream;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "APIs for uploading files")
public class FileController {

    private final CloudinaryService cloudinaryService;
    private final RateLimitService rateLimitService;
    private final RestTemplate restTemplate;
    private final MediaAuthorizationService mediaAuthorizationService;

    @PostMapping("/direct-upload/prepare")
    @Operation(summary = "Check for a duplicate and create a signed direct Cloudinary upload")
    public ResponseEntity<ApiResponse<DirectUploadPrepareResponse>> prepareDirectUpload(
            @Valid @RequestBody DirectUploadPrepareRequest request) {
        rateLimitService.check("file:prepare", rateLimitService.currentUserIdentity(), 60, Duration.ofMinutes(10));
        if (request.getSize() != null && request.getSize() > 50L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File exceeds the 50 MB limit", null));
        }
        DirectUploadPrepareResponse response;
        try {
            response = cloudinaryService.prepareDirectUpload(request);
        } catch (RuntimeException exception) {
            return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage(), null));
        }
        if (response.isDeduplicated() && response.getFile() != null) {
            mediaAuthorizationService.claimUpload(response.getFile().getUrl());
        }
        return ResponseEntity.ok(ApiResponse.success(response,
                response.isDeduplicated() ? "Existing file reused" : "Upload signature created"));
    }

    @PostMapping("/direct-upload/confirm")
    @Operation(summary = "Verify and store a completed direct Cloudinary upload")
    public ResponseEntity<ApiResponse<FileUploadResponse>> confirmDirectUpload(
            @Valid @RequestBody DirectUploadConfirmRequest request) {
        rateLimitService.check("file:confirm", rateLimitService.currentUserIdentity(), 60, Duration.ofMinutes(10));
        try {
            FileUploadResponse response = cloudinaryService.confirmDirectUpload(request);
            mediaAuthorizationService.claimUpload(response.getUrl());
            return ResponseEntity.ok(ApiResponse.success(response, "Direct upload confirmed"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage(), null));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Unable to verify Cloudinary upload", null));
        }
    }

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
                    .fileName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .build();

            mediaAuthorizationService.claimUpload(url);

            return ResponseEntity.ok(ApiResponse.success(response, "File uploaded successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), null));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to upload file: " + e.getMessage(), null));
        }
    }

    @GetMapping("/download")
    @Operation(summary = "Download an uploaded Cloudinary file with its original name")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @RequestParam("url") String url,
            @RequestParam("fileName") String fileName) {
        rateLimitService.check("file:download", rateLimitService.currentUserIdentity(), 60, Duration.ofMinutes(10));
        MediaAsset asset = mediaAuthorizationService.assertCanDownload(url);
        return downloadFromStorage(asset, fileName, true);
    }

    @GetMapping("/content/{assetId}")
    @Operation(summary = "Read a protected attachment")
    public ResponseEntity<StreamingResponseBody> readProtectedAsset(@PathVariable String assetId) {
        rateLimitService.check("file:content", rateLimitService.currentUserIdentity(), 180, Duration.ofMinutes(10));
        MediaAsset asset = mediaAuthorizationService.assertCanDownloadAsset(assetId);
        return downloadFromStorage(asset, "attachment", false);
    }

    private ResponseEntity<StreamingResponseBody> downloadFromStorage(MediaAsset asset, String fileName, boolean attachment) {
        URI source;
        try {
            source = URI.create(cloudinaryService.createDownloadUrl(asset));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        String host = source.getHost();
        if (!"https".equalsIgnoreCase(source.getScheme())
                || host == null
                || !(host.equals("api.cloudinary.com")
                || host.equals("res.cloudinary.com")
                || host.endsWith(".res.cloudinary.com"))) {
            return ResponseEntity.badRequest().build();
        }

        String safeFileName = sanitizeFileName(fileName);
        MediaType mediaType = (asset != null && asset.getContentType() != null && !asset.getContentType().isBlank())
                ? MediaType.parseMediaType(asset.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition((attachment
                ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(safeFileName, StandardCharsets.UTF_8).build());
        headers.setCacheControl("private, no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        if (asset != null && asset.getSize() != null && asset.getSize() > 0) {
            headers.setContentLength(asset.getSize());
        }

        StreamingResponseBody body = outputStream -> {
            restTemplate.execute(source, HttpMethod.GET, null, clientResponse -> {
                if (clientResponse.getStatusCode().is2xxSuccessful()) {
                    try (InputStream inputStream = clientResponse.getBody()) {
                        inputStream.transferTo(outputStream);
                    }
                }
                return null;
            });
        };

        return ResponseEntity.ok().headers(headers).body(body);
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "download";
        String normalized = fileName.replace('\\', '_').replace('/', '_').replaceAll("[\\r\\n\\u0000]", "_").trim();
        return normalized.isBlank() ? "download" : normalized;
    }
}
