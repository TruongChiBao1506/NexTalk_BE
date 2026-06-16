package iuh.fit.se.nextalk_be.sticker;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StickerRepository extends MongoRepository<Sticker, String> {
    List<Sticker> findByPackId(String packId);
}
