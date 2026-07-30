package iuh.fit.se.nextalk_be.service;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DependencyHealthServiceTest {

    @Test
    void readinessIsUpOnlyWhenMongoAndRedisRespond() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        RedisConnection redis = mock(RedisConnection.class);
        when(mongo.executeCommand("{ ping: 1 }")).thenReturn(new Document("ok", 1.0D));
        when(redisFactory.getConnection()).thenReturn(redis);
        when(redis.ping()).thenReturn("PONG");

        DependencyHealthService.ReadinessSnapshot snapshot =
                new DependencyHealthService(mongo, redisFactory).readiness();

        assertTrue(snapshot.ready());
        verify(redis).close();
    }

    @Test
    void readinessIsDownWithoutLeakingDependencyExceptions() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        when(mongo.executeCommand("{ ping: 1 }")).thenThrow(new IllegalStateException("private host"));
        when(redisFactory.getConnection()).thenThrow(new IllegalStateException("private password"));

        DependencyHealthService.ReadinessSnapshot snapshot =
                new DependencyHealthService(mongo, redisFactory).readiness();

        assertFalse(snapshot.ready());
        assertTrue(snapshot.dependencies().values().stream().allMatch("DOWN"::equals));
    }
}
