package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.ChannelTask;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import iuh.fit.se.nextalk_be.entity.ChannelTaskStatus;

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

    @Query(value = "{'archived': {'$ne': true}, 'status': {'$in': ?0}, 'dueAt': {'$ne': null}, '$or': ["
            + "{'reminderSent': {'$ne': true}, 'reminderAt': {'$lte': ?1}},"
            + "{'overdueNotificationSent': {'$ne': true}, 'dueAt': {'$lte': ?1}},"
            + "{'approachingNotificationSent': {'$ne': true}, 'dueAt': {'$gt': ?1, '$lte': ?2}}"
            + "]}",
            sort = "{'dueAt': 1, '_id': 1}")
    List<ChannelTask> findDeadlineCandidates(
            List<ChannelTaskStatus> activeStatuses,
            java.time.LocalDateTime now,
            java.time.LocalDateTime oneHourLater,
            Pageable pageable
    );
}
