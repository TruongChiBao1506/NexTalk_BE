package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.SavedMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedMessageRepository extends MongoRepository<SavedMessage, String> {
    Optional<SavedMessage> findByUserIdAndMessageId(String userId, String messageId);
    List<SavedMessage> findAllByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    void deleteByUserIdAndMessageId(String userId, String messageId);
}
