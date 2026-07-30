package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Converts protected avatar proxy URLs into short-lived provider URLs for push
 * payloads. Native notification renderers cannot attach the app's bearer token,
 * while a permanent public Cloudinary URL would undo private-media delivery.
 */
@Service
@RequiredArgsConstructor
public class NotificationAvatarUrlService {

    private final MediaAssetRepository mediaAssets;
    private final CloudinaryService cloudinaryService;

    public String resolve(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) return "";

        MediaAsset asset = mediaAssets.findByUrl(storedUrl).orElse(null);
        if (asset == null) {
            // Legacy public avatars remain usable until they are migrated.
            return storedUrl;
        }
        if (asset.getStatus() == null || !asset.getStatus().isShareable()) return "";

        try {
            return cloudinaryService.createDownloadUrl(asset);
        } catch (RuntimeException exception) {
            return "";
        }
    }
}
