package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.MessageReminder;
import iuh.fit.se.nextalk_be.entity.MessageReminderStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageReminderRepository extends MongoRepository<MessageReminder, String> {
    List<MessageReminder> findByUserIdOrderByRemindAtAsc(String userId);

    List<MessageReminder> findByStatusAndRemindAtLessThanEqual(MessageReminderStatus status, LocalDateTime remindAt);
}
