package iuh.fit.se.nextalk_be.message;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends MongoRepository<Message, UUID> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    Page<Message> findByConversationIdAndDeletedByUsersNotContainingOrderByCreatedAtDesc(UUID conversationId, UUID userId, Pageable pageable);

    List<Message> findAllByConversationId(UUID conversationId);

    List<Message> findByConversationIdAndIsPinnedTrue(UUID conversationId);

    List<Message> findByConversationIdAndContentContainingIgnoreCaseAndMessageTypeAndIsRecalledFalse(UUID conversationId, String content, MessageType messageType);

    List<Message> findByConversationIdInAndContentContainingIgnoreCaseAndMessageTypeAndIsRecalledFalse(List<UUID> conversationIds, String content, MessageType messageType);
}
