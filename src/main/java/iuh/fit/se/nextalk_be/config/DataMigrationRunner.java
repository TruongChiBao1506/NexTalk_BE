package iuh.fit.se.nextalk_be.config;

import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.ChannelType;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataMigrationRunner implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;
    private final ChannelRepository channelRepository;
    private final GroupRepository groupRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Override
    public void run(String... args) throws Exception {
        migrateGroupsToChannels();
        mergeDuplicateCloudConversations();
    }

    /** Public entry-point so admin endpoints can trigger the merge on-demand. */
    public void runCloudMerge() {
        mergeDuplicateCloudConversations();
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

    /**
     * Merge duplicate CLOUD conversations into one canonical conversation per user.
     *
     * NOTE: Uses raw MongoTemplate to avoid @DocumentReference lazy-loading issues.
     * The Conversation.members field is lazy, so conversationRepository.findAll()
     * returns Conversation objects whose members set is a proxy that won't iterate
     * correctly outside a Spring transaction context.
     */
    private void mergeDuplicateCloudConversations() {
        // Load raw BSON documents to safely read the member array without lazy-loading
        org.springframework.data.mongodb.core.query.Query cloudQuery =
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("type").is("CLOUD")
                );
        List<org.bson.Document> rawClouds = mongoTemplate.find(cloudQuery, org.bson.Document.class, "conversations");

        log.info("[Migration] Found {} total CLOUD conversation documents in DB", rawClouds.size());

        // Group raw docs by first member id
        Map<String, List<org.bson.Document>> byOwner = new java.util.LinkedHashMap<>();
        for (org.bson.Document raw : rawClouds) {
            // members field is stored as a list of DBRefs or ObjectIds
            Object membersObj = raw.get("members");
            String ownerId = null;
            if (membersObj instanceof List) {
                List<?> memberList = (List<?>) membersObj;
                if (!memberList.isEmpty()) {
                    Object first = memberList.get(0);
                    if (first instanceof org.bson.Document) {
                        Object ref = ((org.bson.Document) first).get("$id");
                        if (ref != null) ownerId = ref.toString();
                    } else if (first instanceof com.mongodb.DBRef) {
                        ownerId = ((com.mongodb.DBRef) first).getId().toString();
                    } else if (first instanceof org.bson.types.ObjectId) {
                        ownerId = first.toString();
                    } else {
                        ownerId = String.valueOf(first);
                    }
                }
            }
            if (ownerId == null) {
                log.warn("[Migration] CLOUD conversation {} has no parseable members, skipping", raw.get("_id"));
                continue;
            }
            byOwner.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(raw);
        }

        log.info("[Migration] CLOUD conversations grouped by owner: {}", byOwner.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().size()).collect(Collectors.joining(", ")));

        for (Map.Entry<String, List<org.bson.Document>> entry : byOwner.entrySet()) {
            List<org.bson.Document> userClouds = entry.getValue();
            if (userClouds.size() <= 1) continue;

            // Sort by createdAt ascending – keep the oldest as canonical
            userClouds.sort(Comparator.comparing(
                    d -> {
                        Object ts = d.get("createdAt");
                        if (ts instanceof java.util.Date) return ((java.util.Date) ts).toInstant();
                        return java.time.Instant.MIN;
                    },
                    Comparator.nullsLast(Comparator.naturalOrder())
            ));

            String canonicalId = userClouds.get(0).get("_id").toString();
            List<String> duplicateIds = userClouds.subList(1, userClouds.size()).stream()
                    .map(d -> d.get("_id").toString())
                    .collect(Collectors.toList());

            log.warn("[Migration] User {} has {} CLOUD conversations – merging into canonical {}",
                    entry.getKey(), userClouds.size(), canonicalId);

            Conversation canonical = conversationRepository.findById(canonicalId).orElse(null);
            if (canonical == null) {
                log.error("[Migration] Could not load canonical conversation {}, skipping", canonicalId);
                continue;
            }

            for (String dupId : duplicateIds) {
                Conversation dup = conversationRepository.findById(dupId).orElse(null);
                if (dup == null) continue;

                // Re-parent messages from duplicate → canonical
                List<Message> dupMessages = messageRepository.findAllByConversationId(dupId);
                List<Message> updated = new ArrayList<>();
                for (Message msg : dupMessages) {
                    msg.setConversationId(canonicalId);
                    msg.setConversation(canonical);
                    updated.add(msg);
                }
                if (!updated.isEmpty()) {
                    messageRepository.saveAll(updated);
                    log.info("[Migration] Moved {} messages from CLOUD {} to {}", updated.size(), dupId, canonicalId);
                }
                conversationRepository.delete(dup);
                log.info("[Migration] Deleted duplicate CLOUD conversation {}", dupId);
            }
        }
    }
}
