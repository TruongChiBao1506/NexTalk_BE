package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RateLimitServiceTest {

    @Test
    void inMemoryLimiterDoesNotAddRedisRoundTrips() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitService service = new RateLimitService(redisTemplate);

        assertDoesNotThrow(() ->
                service.checkInMemory("websocket:send", "user-1", 2, Duration.ofMinutes(1)));
        assertDoesNotThrow(() ->
                service.checkInMemory("websocket:send", "user-1", 2, Duration.ofMinutes(1)));
        assertThrows(RateLimitExceededException.class, () ->
                service.checkInMemory("websocket:send", "user-1", 2, Duration.ofMinutes(1)));

        verifyNoInteractions(redisTemplate);
    }
}
