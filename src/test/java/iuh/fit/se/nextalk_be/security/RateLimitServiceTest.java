package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void spoofedForwardedHeaderIsIgnoredByDefault() {
        RateLimitService service = new RateLimitService(mock(StringRedisTemplate.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.99");
        request.addHeader("X-Real-IP", "198.51.100.98");

        assertEquals("203.0.113.10", service.clientIdentity(request));
    }

    @Test
    void forwardedHeaderIsUsedOnlyForConfiguredTrustedProxy() {
        RateLimitService service = new RateLimitService(mock(StringRedisTemplate.class));
        ReflectionTestUtils.setField(service, "trustForwardedHeaders", true);
        ReflectionTestUtils.setField(service, "trustedProxyCidrs", java.util.List.of("10.0.0.0/8"));

        MockHttpServletRequest untrusted = new MockHttpServletRequest();
        untrusted.setRemoteAddr("203.0.113.10");
        untrusted.addHeader("X-Forwarded-For", "198.51.100.99");
        assertEquals("203.0.113.10", service.clientIdentity(untrusted));

        MockHttpServletRequest trusted = new MockHttpServletRequest();
        trusted.setRemoteAddr("10.1.2.3");
        trusted.addHeader("X-Forwarded-For", "198.51.100.99");
        assertEquals("198.51.100.99", service.clientIdentity(trusted));
    }
}
