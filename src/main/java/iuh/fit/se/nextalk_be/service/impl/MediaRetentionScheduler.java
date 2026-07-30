package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.entity.MediaAssetStatus;
import iuh.fit.se.nextalk_be.repository.MediaAssetRepository;
import iuh.fit.se.nextalk_be.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MediaRetentionScheduler {
    private final MediaAssetRepository mediaAssetRepository;
    private final CloudinaryService cloudinaryService;

    @Scheduled(fixedDelay = 60_000L)
    public void deletePendingAssets() {
        for (var asset : mediaAssetRepository.findTop100ByStatus(MediaAssetStatus.PENDING_DELETE)) {
            try {
                cloudinaryService.deleteAsset(asset);
            } catch (Exception exception) {
                log.warn("Unable to purge a pending media asset; it will be retried");
            }
        }
    }
}
