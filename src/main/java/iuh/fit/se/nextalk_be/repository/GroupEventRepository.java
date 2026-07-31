package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.GroupEvent;
import iuh.fit.se.nextalk_be.entity.GroupEventStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GroupEventRepository extends MongoRepository<GroupEvent, String> {
    List<GroupEvent> findAllByGroupIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
            String groupId, GroupEventStatus status, LocalDateTime startsAt);

    List<GroupEvent> findAllByStatusAndReminderSentFalseAndRemindAtLessThanEqual(
            GroupEventStatus status, LocalDateTime now);
}
