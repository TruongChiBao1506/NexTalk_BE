package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.TaskAssistantPendingAction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TaskAssistantPendingActionRepository
        extends MongoRepository<TaskAssistantPendingAction, String> {
    Optional<TaskAssistantPendingAction> findByIdAndUserId(String id, String userId);
}
