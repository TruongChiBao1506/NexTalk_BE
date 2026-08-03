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
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxIndexManager implements ApplicationRunner {
    private static final List<IndexSpec> NOTIFICATION_INDEXES = List.of(
            new IndexSpec(
                    "notification_push_idempotency_unique_v1",
                    new Document("pushIdempotencyKey", 1),
                    true,
                    true),
            new IndexSpec(
                    "notification_outbox_pending_v1",
                    new Document("deliveryStatus", 1)
                            .append("nextDeliveryAttemptAt", 1)
                            .append("createdAt", 1),
                    false,
                    false),
            new IndexSpec(
                    "notification_outbox_lease_v1",
                    new Document("deliveryStatus", 1).append("deliveryLeaseUntil", 1),
                    false,
                    false));
    private static final List<IndexSpec> MESSAGE_INDEXES = List.of(
            new IndexSpec(
                    "message_notification_dispatch_v1",
                    new Document("notificationDispatchStatus", 1)
                            .append("notificationDispatchNextAttemptAt", 1)
                            .append("createdAt", 1),
                    false,
                    false));

    private final MongoTemplate mongoTemplate;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        verifyCollection("notifications", NOTIFICATION_INDEXES, production);
        verifyCollection("messages", MESSAGE_INDEXES, production);
        log.info("Verified notification outbox indexes");
    }

    private void verifyCollection(String collectionName, List<IndexSpec> specs, boolean production) {
        MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);
        List<Document> indexes = collection.listIndexes().into(new ArrayList<>());
        for (IndexSpec spec : specs) {
            if (indexes.stream().anyMatch(index -> spec.matches(index))) {
                continue;
            }
            if (production) {
                throw new IllegalStateException(
                        "Production notification outbox index is missing; run the reviewed outbox index migration before startup");
            }
            indexes.stream()
                    .filter(index -> spec.hasSameKeys(index))
                    .map(index -> index.getString("name"))
                    .filter(name -> name != null && !"_id_".equals(name))
                    .forEach(collection::dropIndex);
            collection.createIndex(
                    spec.keys(),
                    new IndexOptions()
                            .name(spec.name())
                            .unique(spec.unique())
                            .sparse(spec.sparse()));
        }
    }

    private record IndexSpec(String name, Document keys, boolean unique, boolean sparse) {
        boolean hasSameKeys(Document index) {
            return keys.equals(index.get("key", Document.class));
        }

        boolean matches(Document index) {
            return hasSameKeys(index)
                    && (!unique || Boolean.TRUE.equals(index.getBoolean("unique")))
                    && (!sparse || Boolean.TRUE.equals(index.getBoolean("sparse")));
        }
    }
}
