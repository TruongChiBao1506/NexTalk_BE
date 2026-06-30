package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.Sticker;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StickerRepository extends MongoRepository<Sticker, String> {
    List<Sticker> findByPackId(String packId);
}
