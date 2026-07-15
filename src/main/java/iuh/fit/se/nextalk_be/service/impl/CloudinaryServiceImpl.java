package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.CloudinaryService;
import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.repository.MediaAssetRepository;
import iuh.fit.se.nextalk_be.dto.request.DirectUploadConfirmRequest;
import iuh.fit.se.nextalk_be.dto.request.DirectUploadPrepareRequest;
import iuh.fit.se.nextalk_be.dto.response.DirectUploadPrepareResponse;
import iuh.fit.se.nextalk_be.dto.response.FileUploadResponse;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;
    private final MediaAssetRepository mediaAssetRepository;
    private final ConcurrentHashMap<String, Object> uploadLocks = new ConcurrentHashMap<>();

    @Override
    public DirectUploadPrepareResponse prepareDirectUpload(DirectUploadPrepareRequest request) {
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
                "overwrite", true
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
                .url(request.getUrl())
                .publicId(expectedPublicId)
                .resourceType(request.getResourceType())
                .format(request.getFormat())
                .size(request.getBytes() != null ? request.getBytes() : request.getSize())
                .contentType(request.getContentType())
                .createdAt(LocalDateTime.now())
                .build();
        mediaAssetRepository.save(asset);
        return toFileUploadResponse(asset, request.getFileName(), request.getContentType(), asset.getSize());
    }

    public Map uploadFile(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String hash = sha256(bytes);

        MediaAsset existing = mediaAssetRepository.findById(hash).orElse(null);
        if (existing != null) {
            return toUploadResult(existing);
        }

        // Avoid two requests in this application instance uploading the same bytes together.
        Object lock = uploadLocks.computeIfAbsent(hash, ignored -> new Object());
        try {
            synchronized (lock) {
                existing = mediaAssetRepository.findById(hash).orElse(null);
                if (existing != null) {
                    return toUploadResult(existing);
                }

                return uploadAndRemember(file, bytes, hash);
            }
        } finally {
            uploadLocks.remove(hash, lock);
        }
    }

    private Map uploadAndRemember(MultipartFile file, byte[] bytes, String hash) throws IOException {
        String contentType = file.getContentType();
        String resourceType = resourceTypeFor(contentType);

        Map uploadResult = cloudinary.uploader().upload(
                bytes,
                ObjectUtils.asMap(
                        "resource_type", resourceType,
                        "public_id", publicIdFor(hash),
                        "unique_filename", false,
                        // The deterministic public id also prevents duplicate assets across app instances.
                        "overwrite", true
                )
        );

        MediaAsset asset = MediaAsset.builder()
                .hash(hash)
                .url((String) uploadResult.get("secure_url"))
                .publicId((String) uploadResult.get("public_id"))
                .resourceType(stringValue(uploadResult.get("resource_type")))
                .format(stringValue(uploadResult.get("format")))
                .size(file.getSize())
                .contentType(contentType)
                .createdAt(LocalDateTime.now())
                .build();
        mediaAssetRepository.save(asset);
        return uploadResult;
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

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String resourceTypeFor(String contentType) {
        return contentType != null && (contentType.startsWith("audio/") || contentType.startsWith("video/"))
                ? "video" : "auto";
    }

    private String publicIdFor(String hash) {
        return "nextalk/assets/" + hash;
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
}
