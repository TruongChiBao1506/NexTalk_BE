package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.MediaAsset;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MediaAssetRepository extends MongoRepository<MediaAsset, String> {
    java.util.Optional<MediaAsset> findByUrl(String url);
    java.util.List<MediaAsset> findTop100ByStatus(iuh.fit.se.nextalk_be.entity.MediaAssetStatus status);
}
