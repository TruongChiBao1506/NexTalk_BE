package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.ScheduledMessage;
import iuh.fit.se.nextalk_be.entity.ScheduledMessageStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledMessageRepository extends MongoRepository<ScheduledMessage, String> {
    List<ScheduledMessage> findBySenderIdAndStatusOrderByScheduledAtAsc(
            String senderId,
            ScheduledMessageStatus status
    );

    List<ScheduledMessage> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            ScheduledMessageStatus status,
            LocalDateTime scheduledAt
    );
}
