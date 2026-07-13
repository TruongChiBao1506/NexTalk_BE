package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.FileUploadResponse;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.CloudinaryService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "APIs for uploading files")
public class FileController {

    private final CloudinaryService cloudinaryService;
    private final RateLimitService rateLimitService;
    private final RestTemplate restTemplate;

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

            return ResponseEntity.ok(ApiResponse.success(response, "File uploaded successfully"));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to upload file: " + e.getMessage(), null));
        }
    }

    @GetMapping("/download")
    @Operation(summary = "Download an uploaded Cloudinary file with its original name")
    public ResponseEntity<byte[]> downloadFile(
            @RequestParam("url") String url,
            @RequestParam("fileName") String fileName) {
        rateLimitService.check("file:download", rateLimitService.currentUserIdentity(), 60, Duration.ofMinutes(10));

        URI source;
        try {
            source = URI.create(url);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }

        String host = source.getHost();
        if (!"https".equalsIgnoreCase(source.getScheme())
                || host == null
                || !(host.equals("res.cloudinary.com") || host.endsWith(".res.cloudinary.com"))) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ResponseEntity<byte[]> upstream = restTemplate.getForEntity(source, byte[].class);
            if (!upstream.getStatusCode().is2xxSuccessful() || upstream.getBody() == null) {
                return ResponseEntity.status(upstream.getStatusCode()).build();
            }

            String safeFileName = sanitizeFileName(fileName);
            MediaType contentType = upstream.getHeaders().getContentType();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(safeFileName, StandardCharsets.UTF_8)
                    .build());
            headers.setContentLength(upstream.getBody().length);
            return ResponseEntity.ok().headers(headers).body(upstream.getBody());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "download";
        String normalized = fileName.replace('\\', '_').replace('/', '_').replaceAll("[\\r\\n\\u0000]", "_").trim();
        return normalized.isBlank() ? "download" : normalized;
    }
}
