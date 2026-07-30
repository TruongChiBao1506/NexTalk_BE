package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.entity.MediaAssetStatus;
import iuh.fit.se.nextalk_be.repository.MediaAssetRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationAvatarUrlServiceTest {

    private final MediaAssetRepository mediaAssets = mock(MediaAssetRepository.class);
    private final CloudinaryService cloudinaryService = mock(CloudinaryService.class);
    private final NotificationAvatarUrlService service =
            new NotificationAvatarUrlService(mediaAssets, cloudinaryService);

    @Test
    void keepsLegacyPublicAvatarUrl() {
        String url = "https://res.cloudinary.com/demo/image/upload/avatar.jpg";
        when(mediaAssets.findByUrl(url)).thenReturn(Optional.empty());

        assertEquals(url, service.resolve(url));
        verifyNoInteractions(cloudinaryService);
    }

    @Test
    void createsShortLivedUrlForProtectedAvatar() {
        String proxyUrl = "https://api.example.test/api/files/content/hash";
        MediaAsset asset = MediaAsset.builder()
                .url(proxyUrl)
                .status(MediaAssetStatus.BASIC_VALIDATED)
                .build();
        when(mediaAssets.findByUrl(proxyUrl)).thenReturn(Optional.of(asset));
        when(cloudinaryService.createDownloadUrl(asset)).thenReturn("https://signed.example.test/avatar");

        assertEquals("https://signed.example.test/avatar", service.resolve(proxyUrl));
        verify(cloudinaryService).createDownloadUrl(asset);
    }

    @Test
    void doesNotExposeUnvalidatedOrBrokenProtectedAvatar() {
        String quarantinedUrl = "https://api.example.test/api/files/content/quarantined";
        MediaAsset quarantined = MediaAsset.builder()
                .url(quarantinedUrl)
                .status(MediaAssetStatus.QUARANTINED)
                .build();
        when(mediaAssets.findByUrl(quarantinedUrl)).thenReturn(Optional.of(quarantined));

        assertEquals("", service.resolve(quarantinedUrl));
        verifyNoInteractions(cloudinaryService);

        String brokenUrl = "https://api.example.test/api/files/content/broken";
        MediaAsset broken = MediaAsset.builder()
                .url(brokenUrl)
                .status(MediaAssetStatus.CLEAN)
                .build();
        when(mediaAssets.findByUrl(brokenUrl)).thenReturn(Optional.of(broken));
        when(cloudinaryService.createDownloadUrl(broken)).thenThrow(new IllegalStateException("missing metadata"));

        assertEquals("", service.resolve(brokenUrl));
    }
}
