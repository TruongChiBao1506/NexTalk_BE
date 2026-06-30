package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

    Page<Message> findByConversationIdAndDeletedByUsersNotContainingOrderByCreatedAtDesc(String conversationId, String userId, Pageable pageable);

    List<Message> findAllByConversationId(String conversationId);

    List<Message> findByConversationIdAndIsPinnedTrue(String conversationId);

    List<Message> findByConversationIdAndContentContainingIgnoreCaseAndMessageTypeAndIsRecalledFalse(String conversationId, String content, MessageType messageType);

    List<Message> findByConversationIdInAndContentContainingIgnoreCaseAndMessageTypeAndIsRecalledFalse(List<String> conversationIds, String content, MessageType messageType);
}
