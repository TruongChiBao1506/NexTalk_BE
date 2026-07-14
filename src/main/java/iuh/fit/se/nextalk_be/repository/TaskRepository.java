package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.Task;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends MongoRepository<Task, String> {
    List<Task> findByConversationId(String conversationId);
}
