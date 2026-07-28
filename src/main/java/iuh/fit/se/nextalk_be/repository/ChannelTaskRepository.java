package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.ChannelTask;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelTaskRepository extends MongoRepository<ChannelTask, String> {
    List<ChannelTask> findAllByChannelIdAndArchivedNotOrderByCreatedAtDesc(String channelId, boolean archived);
    List<ChannelTask> findAllByChannelIdAndArchivedTrueOrderByCreatedAtDesc(String channelId);
    void deleteAllByChannelId(String channelId);
    void deleteAllByGroupId(String groupId);
}
