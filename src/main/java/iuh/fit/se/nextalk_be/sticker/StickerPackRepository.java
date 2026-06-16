package iuh.fit.se.nextalk_be.sticker;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StickerPackRepository extends MongoRepository<StickerPack, String> {
}
