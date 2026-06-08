package iuh.fit.se.nextalk_be.config;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SetOperations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Configuration
@Profile("test")
@SuppressWarnings("unchecked")
public class TestRedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);

        // Mock ValueOperations for status and lastSeen
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        Map<String, String> mockDb = new HashMap<>();

        when(template.opsForValue()).thenReturn(valueOperations);

        Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String val = invocation.getArgument(1);
            mockDb.put(key, val);
            return null;
        }).when(valueOperations).set(any(String.class), any(String.class));

        when(valueOperations.get(any(String.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return mockDb.get(key);
        });

        // Mock SetOperations for sessions
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        Map<String, Set<String>> mockSetDb = new HashMap<>();

        when(template.opsForSet()).thenReturn(setOperations);

        when(setOperations.add(any(String.class), any(String.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String val = invocation.getArgument(1);
            mockSetDb.computeIfAbsent(key, k -> new HashSet<>()).add(val);
            return 1L;
        });

        when(setOperations.remove(any(String.class), any(Object.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String val = (String) invocation.getArgument(1);
            Set<String> set = mockSetDb.get(key);
            if (set != null) {
                set.remove(val);
            }
            return 1L;
        });

        when(setOperations.size(any(String.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Set<String> set = mockSetDb.get(key);
            return set != null ? (long) set.size() : 0L;
        });

        return template;
    }
}
