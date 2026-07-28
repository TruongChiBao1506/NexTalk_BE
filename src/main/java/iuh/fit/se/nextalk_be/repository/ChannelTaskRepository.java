package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.ChannelTask;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import org.bson.types.ObjectId;

import java.util.List;

@Repository
public interface ChannelTaskRepository extends MongoRepository<ChannelTask, String> {
    List<ChannelTask> findAllByChannelIdAndArchivedNotOrderByCreatedAtDesc(String channelId, boolean archived);
    List<ChannelTask> findAllByChannelIdAndArchivedTrueOrderByCreatedAtDesc(String channelId);
    void deleteAllByChannelId(String channelId);
    void deleteAllByGroupId(String groupId);

    @Query(value = "{'archived': {'$ne': true}, '$or': [{'assignees': ?0}, {'assignees.$id': ?0}, {'assignees._id': ?0}, {'assignees': ?1}, {'assignees.$id': ?1}, {'assignees._id': ?1}]}", sort = "{'dueAt': 1, 'createdAt': -1}")
    List<ChannelTask> findMyActiveTasks(String userId, ObjectId userObjectId);

    @Query(value = "{'archived': true, '$or': [{'assignees': ?0}, {'assignees.$id': ?0}, {'assignees._id': ?0}, {'assignees': ?1}, {'assignees.$id': ?1}, {'assignees._id': ?1}]}", sort = "{'dueAt': 1, 'createdAt': -1}")
    List<ChannelTask> findMyArchivedTasks(String userId, ObjectId userObjectId);
}
