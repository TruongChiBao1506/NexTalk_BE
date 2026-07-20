package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.MessageStatus;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface MessageStatusRepository extends MongoRepository<MessageStatus, String> {

    List<MessageStatus> findAllByMessageId(String messageId);

    List<MessageStatus> findAllByMessageIdIn(Collection<String> messageIds);

    List<MessageStatus> findAllByUserIdAndMessageIdIn(String userId, Collection<String> messageIds);

    List<MessageStatus> findAllByUserIdAndMessageIdInAndStatusIn(String userId, Collection<String> messageIds, Collection<String> statuses);

    List<MessageStatus> findAllByConversationIdAndUserIdAndStatusIn(String conversationId, String userId, Collection<String> statuses);

    @Query(value = "{'$and':[{'conversationId':?0},{'updatedAt':{'$gt':?1,'$lte':?2}}]}", sort = "{'updatedAt':1,'_id':1}")
    List<MessageStatus> findConversationStatusChanges(
            String conversationId,
            LocalDateTime since,
            LocalDateTime until,
            Pageable pageable
    );

    List<MessageStatus> findAllByUserIdAndStatusIn(String userId, Collection<String> statuses);

    List<MessageStatus> findByConversationIdIsNull(Pageable pageable);
}
