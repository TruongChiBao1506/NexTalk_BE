package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.ChannelTaskActivity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelTaskActivityRepository extends MongoRepository<ChannelTaskActivity, String> {
    List<ChannelTaskActivity> findTop200ByGroupIdAndChannelIdOrderByCreatedAtDesc(String groupId, String channelId);

    boolean existsByTaskIdAndType(String taskId, iuh.fit.se.nextalk_be.entity.TaskActivityType type);

    @Query("{'groupId': ?0, 'channelId': ?1, 'readByUserIds': {'$ne': ?2}}")
    @Update("{'$addToSet': {'readByUserIds': ?2}}")
    long markAllAsRead(String groupId, String channelId, String userId);
}
