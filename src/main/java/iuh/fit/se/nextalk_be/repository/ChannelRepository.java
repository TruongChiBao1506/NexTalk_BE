package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.Channel;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelRepository extends MongoRepository<Channel, String> {
    List<Channel> findAllByGroupId(String groupId);
    void deleteAllByGroupId(String groupId);
    java.util.Optional<Channel> findByConversationId(String conversationId);
    List<Channel> findAllByConversationIdIn(java.util.Collection<String> conversationIds);
}
