package iuh.fit.se.nextalk_be.config;

import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.ChannelType;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;


import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DataMigrationRunner implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;
    private final ChannelRepository channelRepository;
    private final GroupRepository groupRepository;
    private final ConversationRepository conversationRepository;

    @Override
    public void run(String... args) throws Exception {
        migrateGroupsToChannels();
    }

    private void migrateGroupsToChannels() {
        List<Map> rawGroups = mongoTemplate.findAll(Map.class, "groups");
        for (Map rawGroup : rawGroups) {
            Object idObj = rawGroup.get("_id");
            if (idObj == null) continue;
            String groupId = idObj.toString();
            
            List<Channel> existingChannels = channelRepository.findAllByGroupId(groupId);
            if (existingChannels.isEmpty()) {
                Object conversationRef = rawGroup.get("conversation");
                if (conversationRef != null) {
                    String convId = null;
                    if (conversationRef instanceof Map) {
                        convId = ((Map<?, ?>) conversationRef).get("$id").toString();
                    } else if (conversationRef instanceof org.bson.Document) {
                         convId = ((org.bson.Document) conversationRef).get("$id").toString();
                    } else if (conversationRef instanceof com.mongodb.DBRef) {
                         convId = ((com.mongodb.DBRef) conversationRef).getId().toString();
                    }

                    if (convId != null) {
                        Conversation conv = conversationRepository.findById(convId).orElse(null);
                        Group group = groupRepository.findById(groupId).orElse(null);

                        if (conv != null && group != null) {
                            Channel defaultChannel = Channel.builder()
                                    .name("Chung")
                                    .type(ChannelType.TEXT)
                                    .isPrivate(false)
                                    .group(group)
                                    .conversation(conv)
                                    .build();
                            channelRepository.save(defaultChannel);
                        }
                    }
                }
            }
        }
    }
}
