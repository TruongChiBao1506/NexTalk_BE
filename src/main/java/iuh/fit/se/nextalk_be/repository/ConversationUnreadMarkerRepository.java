package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.ConversationUnreadMarker;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationUnreadMarkerRepository extends MongoRepository<ConversationUnreadMarker, String> {
    Optional<ConversationUnreadMarker> findByUserIdAndConversationId(String userId, String conversationId);
    List<ConversationUnreadMarker> findAllByUserId(String userId);
    void deleteByUserIdAndConversationId(String userId, String conversationId);
}
