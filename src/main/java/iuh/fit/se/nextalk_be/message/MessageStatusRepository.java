package iuh.fit.se.nextalk_be.message;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MessageStatusRepository extends MongoRepository<MessageStatus, String> {

    List<MessageStatus> findAllByMessageId(String messageId);

    List<MessageStatus> findAllByMessageIdIn(Collection<String> messageIds);

    List<MessageStatus> findAllByUserIdAndMessageIdIn(String userId, Collection<String> messageIds);

    List<MessageStatus> findAllByUserIdAndMessageIdInAndStatusIn(String userId, Collection<String> messageIds, Collection<String> statuses);
}
