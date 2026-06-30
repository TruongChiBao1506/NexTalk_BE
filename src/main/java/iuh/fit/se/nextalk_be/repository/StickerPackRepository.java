package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.StickerPack;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StickerPackRepository extends MongoRepository<StickerPack, String> {
}
