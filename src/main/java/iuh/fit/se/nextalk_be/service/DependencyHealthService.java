package iuh.fit.se.nextalk_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DependencyHealthService {

    private final MongoTemplate mongoTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    public ReadinessSnapshot readiness() {
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("mongodb", mongoReady() ? "UP" : "DOWN");
        dependencies.put("redis", redisReady() ? "UP" : "DOWN");
        boolean ready = dependencies.values().stream().allMatch("UP"::equals);
        return new ReadinessSnapshot(ready, Map.copyOf(dependencies));
    }

    private boolean mongoReady() {
        try {
            Object result = mongoTemplate.executeCommand("{ ping: 1 }").get("ok");
            return result instanceof Number number && number.doubleValue() == 1.0D;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean redisReady() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public record ReadinessSnapshot(boolean ready, Map<String, String> dependencies) {
    }
}
