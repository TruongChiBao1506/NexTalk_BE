package iuh.fit.se.nextalk_be.message;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageStatusRepository extends MongoRepository<MessageStatus, UUID> {

    List<MessageStatus> findAllByMessageId(UUID messageId);

    List<MessageStatus> findAllByMessageIdIn(Collection<UUID> messageIds);

    List<MessageStatus> findAllByUserIdAndMessageIdIn(UUID userId, Collection<UUID> messageIds);

    List<MessageStatus> findAllByUserIdAndMessageIdInAndStatusIn(UUID userId, Collection<UUID> messageIds, Collection<String> statuses);
}
