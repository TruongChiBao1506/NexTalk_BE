package iuh.fit.se.nextalk_be.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
@Slf4j
public class MessageQueryIndexManager implements ApplicationRunner {
    private static final List<IndexSpec> QUERY_INDEXES = List.of(
            new IndexSpec("message_history_cursor_v1",
                    new Document("conversationId", 1).append("createdAt", -1).append("_id", -1)),
            new IndexSpec("message_pinned_cursor_v1",
                    new Document("conversationId", 1).append("isPinned", 1)
                            .append("createdAt", -1).append("_id", -1)),
            new IndexSpec("message_search_filter_cursor_v1",
                    new Document("conversationId", 1).append("isRecalled", 1)
                            .append("messageType", 1).append("senderId", 1)
                            .append("createdAt", -1).append("_id", -1)));
    private static final String TEXT_INDEX_NAME = "message_content_text_v1";

    private final MongoTemplate mongoTemplate;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        MongoCollection<Document> messages = mongoTemplate.getCollection("messages");
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        List<Document> indexes = messages.listIndexes().into(new ArrayList<>());

        for (IndexSpec spec : QUERY_INDEXES) {
            if (indexes.stream().anyMatch(spec::matches)) continue;
            if (production) throw missingProductionIndex();
            dropSameKeyIndexes(messages, indexes, spec.keys());
            messages.createIndex(spec.keys(), new IndexOptions().name(spec.name()));
        }

        if (indexes.stream().noneMatch(MessageQueryIndexManager::isContentTextIndex)) {
            if (production) throw missingProductionIndex();
            indexes.stream()
                    .filter(MessageQueryIndexManager::isAnyTextIndex)
                    .map(index -> index.getString("name"))
                    .filter(name -> name != null && !"_id_".equals(name))
                    .forEach(messages::dropIndex);
            messages.createIndex(
                    new Document("content", "text"),
                    new IndexOptions().name(TEXT_INDEX_NAME).defaultLanguage("none"));
        }
        log.info("Verified message history and search indexes");
    }

    private static void dropSameKeyIndexes(
            MongoCollection<Document> collection,
            List<Document> indexes,
            Document keys
    ) {
        indexes.stream()
                .filter(index -> keys.equals(index.get("key", Document.class)))
                .map(index -> index.getString("name"))
                .filter(name -> name != null && !"_id_".equals(name))
                .forEach(collection::dropIndex);
    }

    private static boolean isContentTextIndex(Document index) {
        Document weights = index.get("weights", Document.class);
        return isAnyTextIndex(index) && weights != null && weights.containsKey("content");
    }

    private static boolean isAnyTextIndex(Document index) {
        Document keys = index.get("key", Document.class);
        return keys != null && (keys.containsValue("text") || keys.containsKey("_fts"));
    }

    private static IllegalStateException missingProductionIndex() {
        return new IllegalStateException(
                "Production message query index is missing; run the reviewed message query index migration before startup");
    }

    private record IndexSpec(String name, Document keys) {
        boolean matches(Document index) {
            return keys.equals(index.get("key", Document.class));
        }
    }
}
