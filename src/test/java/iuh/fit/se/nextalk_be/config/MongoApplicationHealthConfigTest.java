package iuh.fit.se.nextalk_be.config;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoApplicationHealthConfigTest {

    private final MongoApplicationHealthConfig config = new MongoApplicationHealthConfig();

    @Test
    void indicatorPingsOnlyTheApplicationMongoTemplate() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.executeCommand("{ ping: 1 }")).thenReturn(new Document("ok", 1.0D));

        Health health = config.mongoHealthIndicator(mongoTemplate).health();

        assertEquals(Status.UP, health.getStatus());
        assertTrue(health.getDetails().isEmpty());
        verify(mongoTemplate).executeCommand("{ ping: 1 }");
    }

    @Test
    void indicatorReportsDownWithoutLeakingTheMongoException() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.executeCommand("{ ping: 1 }"))
                .thenThrow(new IllegalStateException("sensitive connection detail"));
        HealthIndicator indicator = config.mongoHealthIndicator(mongoTemplate);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().isEmpty());
    }

    @Test
    void indicatorReportsDownWhenMongoDoesNotAcknowledgeThePing() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.executeCommand("{ ping: 1 }")).thenReturn(new Document("ok", 0));

        Health health = config.mongoHealthIndicator(mongoTemplate).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().isEmpty());
    }
}
