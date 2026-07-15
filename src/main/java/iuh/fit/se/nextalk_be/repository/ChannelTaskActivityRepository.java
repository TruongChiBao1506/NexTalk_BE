package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.ChannelTaskActivity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelTaskActivityRepository extends MongoRepository<ChannelTaskActivity, String> {
    List<ChannelTaskActivity> findAllByGroupIdAndChannelIdOrderByCreatedAtDesc(String groupId, String channelId);
}
