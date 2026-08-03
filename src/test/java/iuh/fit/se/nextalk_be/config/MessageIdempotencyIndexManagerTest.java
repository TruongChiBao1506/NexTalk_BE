package iuh.fit.se.nextalk_be.config;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MessageIdempotencyIndexManagerTest {
    private static final Document TARGET_KEYS = new Document("conversationId", 1)
            .append("senderId", 1)
            .append("metadata.clientMessageId", 1);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private Environment environment;

    @Test
    void nonProductionStartupPreservesMessagesAndReconcilesLegacyRetryKeys() {
        MongoCollection<Document> messages = mongoTemplate.getCollection("messages");
        ObjectId firstId = new ObjectId();
        ObjectId secondId = new ObjectId();
        String conversationId = "legacy-conversation";
        String senderId = "legacy-sender";
        String clientMessageId = "legacy-retry-key";

        dropTargetIndexes(messages);
        try {
            messages.insertMany(List.of(
                    legacyMessage(firstId, conversationId, senderId, clientMessageId),
                    legacyMessage(secondId, conversationId, senderId, clientMessageId)));

            new MessageIdempotencyIndexManager(mongoTemplate, environment)
                    .run(new DefaultApplicationArguments(new String[0]));

            assertThat(messages.countDocuments(new Document("_id", new Document("$in", List.of(firstId, secondId)))))
                    .isEqualTo(2);
            assertThat(messages.countDocuments(new Document("metadata.clientMessageId", clientMessageId)))
                    .isEqualTo(1);
            assertThat(messages.listIndexes().into(new ArrayList<>()))
                    .anyMatch(MessageIdempotencyIndexManager::isReadyIndex);
        } finally {
            messages.deleteMany(new Document("_id", new Document("$in", List.of(firstId, secondId))));
            ensureTargetIndex(messages);
        }
    }

    private static Document legacyMessage(
            ObjectId id,
            String conversationId,
            String senderId,
            String clientMessageId) {
        return new Document("_id", id)
                .append("conversationId", conversationId)
                .append("senderId", senderId)
                .append("metadata", new Document("clientMessageId", clientMessageId));
    }

    private static void dropTargetIndexes(MongoCollection<Document> messages) {
        for (Document index : messages.listIndexes()) {
            if (TARGET_KEYS.equals(index.get("key", Document.class))) {
                messages.dropIndex(index.getString("name"));
            }
        }
    }

    private void ensureTargetIndex(MongoCollection<Document> messages) {
        boolean ready = messages.listIndexes().into(new ArrayList<>()).stream()
                .anyMatch(MessageIdempotencyIndexManager::isReadyIndex);
        if (!ready) {
            new MessageIdempotencyIndexManager(mongoTemplate, environment)
                    .run(new DefaultApplicationArguments(new String[0]));
        }
    }
}
