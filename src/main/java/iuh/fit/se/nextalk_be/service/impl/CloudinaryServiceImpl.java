package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.CloudinaryService;
import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.entity.MediaAssetStatus;
import iuh.fit.se.nextalk_be.repository.MediaAssetRepository;
import iuh.fit.se.nextalk_be.dto.request.DirectUploadConfirmRequest;
import iuh.fit.se.nextalk_be.dto.request.DirectUploadPrepareRequest;
import iuh.fit.se.nextalk_be.dto.response.DirectUploadPrepareResponse;
import iuh.fit.se.nextalk_be.dto.response.FileUploadResponse;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.security.FileContentInspector;
import iuh.fit.se.nextalk_be.security.MalwareScanner;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.Instant;
import java.net.URI;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024;
    private static final long DEFAULT_MAX_RAW_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/quicktime",
            "audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav", "audio/webm",
            "application/pdf", "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip"
    );

    private final Cloudinary cloudinary;
    private final MediaAssetRepository mediaAssetRepository;
    private final FileContentInspector fileContentInspector;
    private final MalwareScanner malwareScanner;
    private final ConcurrentHashMap<String, Object> uploadLocks = new ConcurrentHashMap<>();

    @Value("${cloudinary.max-raw-file-size:" + DEFAULT_MAX_RAW_UPLOAD_BYTES + "}")
    private long maxRawUploadBytes = DEFAULT_MAX_RAW_UPLOAD_BYTES;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Value("${app.file-security.direct-upload-enabled:false}")
    private boolean directUploadEnabled;

    @Override
    public DirectUploadPrepareResponse prepareDirectUpload(DirectUploadPrepareRequest request) {
        if (!directUploadEnabled) {
            throw new BadRequestException("Direct upload is disabled; use the verified upload endpoint");
        }
        validateUploadMetadata(request.getContentType(), request.getSize());
        String hash = request.getHash().toLowerCase();
        MediaAsset existing = mediaAssetRepository.findById(hash).orElse(null);
        if (existing != null) {
            return DirectUploadPrepareResponse.builder()
                    .deduplicated(true)
                    .file(toFileUploadResponse(existing, request.getFileName(), request.getContentType(), request.getSize()))
                    .build();
        }

        String resourceType = resourceTypeFor(request.getContentType());
        String publicId = publicIdFor(hash);
        long timestamp = Instant.now().getEpochSecond();
        Map<String, Object> signedParams = ObjectUtils.asMap(
                "timestamp", timestamp,
                "public_id", publicId,
                "overwrite", false,
                "type", "authenticated"
        );
        String signature = cloudinary.apiSignRequest(signedParams, cloudinary.config.apiSecret);
        String uploadPrefix = cloudinary.config.uploadPrefix;
        if (uploadPrefix == null || uploadPrefix.isBlank() || "null".equalsIgnoreCase(uploadPrefix)) {
            uploadPrefix = "https://api.cloudinary.com";
        }
        uploadPrefix = uploadPrefix.replaceAll("/+$", "");
        String uploadUrl = uploadPrefix + "/v1_1/" + cloudinary.config.cloudName
                + "/" + resourceType + "/upload";

        return DirectUploadPrepareResponse.builder()
                .deduplicated(false)
                .cloudName(cloudinary.config.cloudName)
                .apiKey(cloudinary.config.apiKey)
                .timestamp(timestamp)
                .signature(signature)
                .publicId(publicId)
                .resourceType(resourceType)
                .uploadUrl(uploadUrl)
                .build();
    }

    @Override
    public FileUploadResponse confirmDirectUpload(DirectUploadConfirmRequest request) throws Exception {
        if (!directUploadEnabled) {
            throw new BadRequestException("Direct upload is disabled; use the verified upload endpoint");
        }
        validateUploadMetadata(request.getContentType(), request.getBytes() != null ? request.getBytes() : request.getSize());
        String hash = request.getHash().toLowerCase();
        MediaAsset existing = mediaAssetRepository.findById(hash).orElse(null);
        if (existing != null) {
            return toFileUploadResponse(existing, request.getFileName(), request.getContentType(), request.getSize());
        }

        String expectedPublicId = publicIdFor(hash);
        if (!expectedPublicId.equals(request.getPublicId())) {
            throw new IllegalArgumentException("publicId does not match the file hash");
        }
        if (!request.getResourceType().matches("image|video|raw")) {
            throw new IllegalArgumentException("Unsupported Cloudinary resource type");
        }
        String expectedResourceType = resourceTypeFor(request.getContentType());
        if (!"auto".equals(expectedResourceType) && !expectedResourceType.equals(request.getResourceType())) {
            throw new IllegalArgumentException("Cloudinary resource type does not match content type");
        }

        if (!cloudinary.verifyApiResponseSignature(
                expectedPublicId, request.getVersion(), request.getResponseSignature())) {
            throw new IllegalArgumentException("Invalid Cloudinary response signature");
        }
        URI secureUrl;
        try {
            secureUrl = URI.create(request.getUrl());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Cloudinary URL");
        }
        String expectedPrefix = "/" + cloudinary.config.cloudName + "/";
        if (!"https".equalsIgnoreCase(secureUrl.getScheme())
                || !"res.cloudinary.com".equalsIgnoreCase(secureUrl.getHost())
                || !secureUrl.getPath().startsWith(expectedPrefix)
                || !secureUrl.getPath().contains("/" + expectedPublicId)) {
            throw new IllegalArgumentException("Cloudinary URL does not match the signed asset");
        }

        MediaAsset asset = MediaAsset.builder()
                .hash(hash)
                .url(proxyUrl(hash))
                .storageUrl(request.getUrl())
                .publicId(expectedPublicId)
                .resourceType(request.getResourceType())
                .format(request.getFormat())
                .size(request.getBytes() != null ? request.getBytes() : request.getSize())
                .contentType(request.getContentType())
                .createdAt(LocalDateTime.now())
                .status(MediaAssetStatus.QUARANTINED)
                .build();
        // Direct uploads remain quarantined because the backend has not inspected
        // the exact bytes. Production disables this route.
        mediaAssetRepository.save(asset);
        throw new BadRequestException("Direct upload is quarantined and cannot be shared");
    }

    public Map uploadFile(MultipartFile file) throws IOException {
        validateUploadMetadata(file.getContentType(), file.getSize());
        byte[] bytes = file.getBytes();
        fileContentInspector.validate(bytes, file.getContentType(), file.getOriginalFilename());
        String hash = sha256(bytes);

        MediaAsset existing = mediaAssetRepository.findById(hash).orElse(null);
        if (isReusable(existing)) {
            return toUploadResult(existing);
        }

        // Avoid two requests in this application instance uploading the same bytes together.
        Object lock = uploadLocks.computeIfAbsent(hash, ignored -> new Object());
        try {
            synchronized (lock) {
                existing = mediaAssetRepository.findById(hash).orElse(null);
                if (isReusable(existing)) {
                    return toUploadResult(existing);
                }

                return uploadAndRemember(bytes, hash, file.getContentType(), file.getOriginalFilename());
            }
        } finally {
            uploadLocks.remove(hash, lock);
        }
    }

    @Override
    public FileUploadResponse uploadGeneratedImage(byte[] data, String contentType, String fileName) throws IOException {
        validateUploadMetadata(contentType, data == null ? 0L : (long) data.length);
        if (!contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Generated asset must be an image");
        }
        fileContentInspector.validate(data, contentType, fileName);
        String hash = sha256(data);
        MediaAsset existing = mediaAssetRepository.findById(hash).orElse(null);
        if (!isReusable(existing)) {
            Object lock = uploadLocks.computeIfAbsent(hash, ignored -> new Object());
            try {
                synchronized (lock) {
                    existing = mediaAssetRepository.findById(hash).orElse(null);
                    if (!isReusable(existing)) {
                        uploadAndRemember(data, hash, contentType, fileName);
                        existing = mediaAssetRepository.findById(hash)
                                .orElseThrow(() -> new IOException("Generated image was not registered"));
                    }
                }
            } finally {
                uploadLocks.remove(hash, lock);
            }
        }
        return toFileUploadResponse(existing, fileName, contentType, (long) data.length);
    }

    private Map uploadAndRemember(byte[] bytes, String hash, String contentType, String fileName) throws IOException {
        String resourceType = resourceTypeFor(contentType);
        MediaAsset asset = mediaAssetRepository.findById(hash).orElseGet(() -> {
            MediaAsset quarantine = MediaAsset.builder()
                    .hash(hash)
                    .url(proxyUrl(hash))
                    .publicId(publicIdFor(hash))
                    .resourceType(resourceType)
                    .size((long) bytes.length)
                    .contentType(contentType)
                    .createdAt(LocalDateTime.now())
                    .status(MediaAssetStatus.QUARANTINED)
                    .build();
            return mediaAssetRepository.save(quarantine);
        });

        boolean malwareScanned;
        try {
            fileContentInspector.validate(bytes, contentType, fileName);
            malwareScanned = malwareScanner.assertClean(bytes);
            if (!malwareScanned) {
                fileContentInspector.validateBasicMode(contentType);
            }
        } catch (RuntimeException exception) {
            asset.setStatus(MediaAssetStatus.REJECTED);
            mediaAssetRepository.save(asset);
            throw exception;
        }

        Map uploadResult = cloudinary.uploader().upload(
                bytes,
                ObjectUtils.asMap(
                        "resource_type", resourceType,
                        "public_id", publicIdFor(hash),
                        "unique_filename", false,
                        "overwrite", false,
                        "type", "authenticated"
                )
        );

        asset.setUrl(proxyUrl(hash));
        asset.setPublicId((String) uploadResult.get("public_id"));
        asset.setResourceType(stringValue(uploadResult.get("resource_type")));
        asset.setFormat(stringValue(uploadResult.get("format")));
        // Provider delivery URLs are minted only at download time and are never
        // persisted. This prevents a database leak exposing a reusable URL.
        asset.setStorageUrl(null);
        asset.setSize((long) bytes.length);
        asset.setContentType(contentType);
        asset.setScannedAt(malwareScanned ? LocalDateTime.now() : null);
        asset.setStatus(malwareScanned ? MediaAssetStatus.CLEAN : MediaAssetStatus.BASIC_VALIDATED);
        mediaAssetRepository.save(asset);
        return toUploadResult(asset);
    }

    private String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is not available", exception);
        }
    }

    private Map<String, Object> toUploadResult(MediaAsset asset) {
        Map<String, Object> result = new HashMap<>();
        result.put("secure_url", asset.getUrl());
        result.put("public_id", asset.getPublicId());
        result.put("resource_type", asset.getResourceType());
        result.put("format", asset.getFormat());
        result.put("bytes", asset.getSize());
        result.put("deduplicated", true);
        return result;
    }

    private boolean isReusable(MediaAsset asset) {
        return asset != null
                && asset.getStatus() != null
                && asset.getStatus().isShareable()
                && asset.getPublicId() != null
                && !asset.getPublicId().isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String resourceTypeFor(String contentType) {
        if (contentType != null && contentType.startsWith("image/")) {
            return "image";
        }
        if (contentType != null && (contentType.startsWith("audio/") || contentType.startsWith("video/"))) {
            return "video";
        }
        // ZIP, PDF, Office documents and other non-media assets must be stored
        // as-is. Using the explicit raw endpoint also avoids auto detection
        // rejecting generated ZIP archives.
        return "raw";
    }

    private void validateUploadMetadata(String contentType, Long size) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported file type");
        }
        if (size == null || size <= 0 || size > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File size must be between 1 byte and 50 MB");
        }
        if ("raw".equals(resourceTypeFor(contentType)) && size > maxRawUploadBytes) {
            throw new IllegalArgumentException(
                    "Raw file exceeds the Cloudinary limit of " + (maxRawUploadBytes / 1024 / 1024) + " MB"
            );
        }
    }

    private String publicIdFor(String hash) {
        return "nextalk/assets/" + hash;
    }

    private String proxyUrl(String hash) {
        return org.springframework.web.util.UriComponentsBuilder
                .fromUriString(publicBaseUrl)
                .path("/api/files/content/")
                .path(hash)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public String createDownloadUrl(MediaAsset asset) {
        if (asset == null
                || asset.getPublicId() == null || asset.getPublicId().isBlank()
                || asset.getFormat() == null || asset.getFormat().isBlank()) {
            throw new IllegalStateException("Private media metadata is incomplete");
        }
        String resourceType = asset.getResourceType() == null || asset.getResourceType().isBlank()
                ? "image"
                : asset.getResourceType();
        try {
            return cloudinary.privateDownload(
                    asset.getPublicId(),
                    asset.getFormat(),
                    ObjectUtils.asMap(
                            "resource_type", resourceType,
                            "type", "authenticated",
                            "attachment", false,
                            "expires_at", Instant.now().plusSeconds(600).getEpochSecond()
                    )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create a private media download URL", exception);
        }
    }

    private FileUploadResponse toFileUploadResponse(MediaAsset asset, String fileName, String contentType, Long size) {
        return FileUploadResponse.builder()
                .url(asset.getUrl())
                .publicId(asset.getPublicId())
                .fileName(fileName)
                .contentType(contentType != null ? contentType : asset.getContentType())
                .size(size != null ? size : asset.getSize())
                .build();
    }

    @Override
    public void deleteAsset(MediaAsset asset) throws IOException {
        if (asset == null || asset.getPublicId() == null || asset.getPublicId().isBlank()) return;
        cloudinary.uploader().destroy(asset.getPublicId(), ObjectUtils.asMap(
                "resource_type", asset.getResourceType(),
                "type", "authenticated",
                "invalidate", true
        ));
        mediaAssetRepository.delete(asset);
    }
}
