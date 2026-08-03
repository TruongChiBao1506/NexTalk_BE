package iuh.fit.se.nextalk_be.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class MessageIdempotencyIndexManager implements ApplicationRunner {
    public static final String INDEX_NAME = "msg_sender_client_id_unique_v2";
    private static final Document INDEX_KEYS = new Document("conversationId", 1)
            .append("senderId", 1)
            .append("metadata.clientMessageId", 1);
    private static final Document PARTIAL_FILTER = new Document(
            "metadata.clientMessageId", new Document("$type", "string"));

    private final MongoTemplate mongoTemplate;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        MongoCollection<Document> messages = mongoTemplate.getCollection("messages");
        List<Document> indexes = messages.listIndexes().into(new ArrayList<>());
        if (indexes.stream().anyMatch(MessageIdempotencyIndexManager::isReadyIndex)) {
            return;
        }

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "Production message idempotency index is missing; run the reviewed index migration before startup");
        }

        for (Document index : indexes) {
            if (hasTargetKeys(index)) {
                String name = index.getString("name");
                if (name != null && !"_id_".equals(name)) {
                    messages.dropIndex(name);
                }
            }
        }
        long reconciledMessages = reconcileLegacyDuplicates(messages);
        messages.createIndex(
                INDEX_KEYS,
                new IndexOptions()
                        .name(INDEX_NAME)
                        .unique(true)
                        .partialFilterExpression(PARTIAL_FILTER));
        log.info("Verified unique message idempotency index after reconciling {} legacy duplicate message(s)",
                reconciledMessages);
    }

    /**
     * Keeps every legacy message, but removes the retry key from later copies so
     * that only the earliest message remains the canonical idempotency result.
     * This is intentionally limited to non-production startup by {@link #run}.
     */
    static long reconcileLegacyDuplicates(MongoCollection<Document> messages) {
        List<Document> duplicateGroups = messages.aggregate(List.of(
                new Document("$match", new Document(
                        "metadata.clientMessageId", new Document("$type", "string"))),
                new Document("$sort", new Document("createdAt", 1).append("_id", 1)),
                new Document("$group", new Document("_id", new Document("conversationId", "$conversationId")
                        .append("senderId", "$senderId")
                        .append("clientMessageId", "$metadata.clientMessageId"))
                        .append("ids", new Document("$push", "$_id"))
                        .append("count", new Document("$sum", 1))),
                new Document("$match", new Document("count", new Document("$gt", 1)))
        )).allowDiskUse(true).into(new ArrayList<>());

        long reconciledMessages = 0;
        for (Document group : duplicateGroups) {
            List<Object> ids = group.getList("ids", Object.class);
            if (ids == null || ids.size() < 2) {
                continue;
            }
            List<Object> laterIds = new ArrayList<>(ids.subList(1, ids.size()));
            UpdateResult result = messages.updateMany(
                    new Document("_id", new Document("$in", laterIds)),
                    new Document("$unset", new Document("metadata.clientMessageId", "")));
            reconciledMessages += result.getModifiedCount();
        }
        return reconciledMessages;
    }

    static boolean isReadyIndex(Document index) {
        return hasTargetKeys(index)
                && Boolean.TRUE.equals(index.getBoolean("unique"))
                && PARTIAL_FILTER.equals(index.get("partialFilterExpression", Document.class));
    }

    private static boolean hasTargetKeys(Document index) {
        return INDEX_KEYS.equals(index.get("key", Document.class));
    }
}
