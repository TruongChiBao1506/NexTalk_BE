package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.PresenceService;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.UserRepository;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    @Value("${app.redis.presence-ttl:120s}")
    private Duration presenceTtl;

    @Value("${app.redis.last-seen-ttl:30d}")
    private Duration lastSeenTtl;

    private static final String SESSIONS_KEY_PREFIX = "nextalk:presence:sessions:";
    private static final String STATUS_KEY_PREFIX = "nextalk:presence:status:";
    private static final String LAST_SEEN_KEY_PREFIX = "nextalk:presence:last_seen:";

    public void addSession(String userId, String sessionId) {
        String sessionsKey = SESSIONS_KEY_PREFIX + userId;
        redisTemplate.opsForHash().put(sessionsKey, sessionId, String.valueOf(System.currentTimeMillis()));
        redisTemplate.expire(sessionsKey, presenceTtl);

        String currentStatus = getUserStatus(userId);
        if (!"ONLINE".equalsIgnoreCase(currentStatus) && !"AWAY".equalsIgnoreCase(currentStatus)) {
            setUserStatus(userId, "ONLINE");
        }
    }

    public boolean removeSession(String userId, String sessionId) {
        String sessionsKey = SESSIONS_KEY_PREFIX + userId;
        Long removed = redisTemplate.opsForHash().delete(sessionsKey, sessionId);
        if (removed == null || removed == 0) {
            return false;
        }

        Long count = redisTemplate.opsForHash().size(sessionsKey);
        if (count == null || count == 0) {
            setUserStatus(userId, "OFFLINE");
            setLastSeen(userId, LocalDateTime.now());
        }
        return true;
    }

    public void setUserStatus(String userId, String status) {
        String statusKey = STATUS_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(statusKey, status);
        redisTemplate.expire(statusKey, presenceTtl);

        // Also sync to MongoDB user document so queries that map database entries are correct
        userRepository.findById(userId).ifPresent(user -> {
            user.setStatus(status);
            userRepository.save(user);
        });
    }

    public String getUserStatus(String userId) {
        String statusKey = STATUS_KEY_PREFIX + userId;
        String status = redisTemplate.opsForValue().get(statusKey);
        if (status != null) {
            return status;
        }
        // No Redis presence means there is no tracked live WebSocket session.
        // MongoDB may contain a stale value from a previous process lifetime.
        return "OFFLINE";
    }

    public void setLastSeen(String userId, LocalDateTime lastSeenTime) {
        String lastSeenKey = LAST_SEEN_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(lastSeenKey, lastSeenTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        redisTemplate.expire(lastSeenKey, lastSeenTtl);
    }

    public LocalDateTime getUserLastSeen(String userId) {
        String lastSeenKey = LAST_SEEN_KEY_PREFIX + userId;
        String val = redisTemplate.opsForValue().get(lastSeenKey);
        if (val == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(val, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    public void touchSession(String userId, String sessionId) {
        String sessionsKey = SESSIONS_KEY_PREFIX + userId;
        redisTemplate.opsForHash().put(
                sessionsKey,
                sessionId,
                String.valueOf(System.currentTimeMillis())
        );
        redisTemplate.expire(sessionsKey, presenceTtl);
        redisTemplate.expire(STATUS_KEY_PREFIX + userId, presenceTtl);
    }

    public List<String> expireStaleSessions(long staleBeforeEpochMillis) {
        List<String> offlineUserIds = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(SESSIONS_KEY_PREFIX + "*").count(100).build();
        Cursor<String> cursor = redisTemplate.scan(options);
        if (cursor == null) return offlineUserIds;
        try (Cursor<String> keys = cursor) {
          while (keys.hasNext()) {
            String key = keys.next();
            Map<Object, Object> sessions = redisTemplate.opsForHash().entries(key);
            sessions.forEach((sessionId, lastHeartbeat) -> {
                try {
                    if (Long.parseLong(String.valueOf(lastHeartbeat)) < staleBeforeEpochMillis) {
                        redisTemplate.opsForHash().delete(key, sessionId);
                    }
                } catch (NumberFormatException ignored) {
                    redisTemplate.opsForHash().delete(key, sessionId);
                }
            });

            Long remaining = redisTemplate.opsForHash().size(key);
            if (remaining == null || remaining == 0) {
                String userId = key.substring(SESSIONS_KEY_PREFIX.length());
                setUserStatus(userId, "OFFLINE");
                setLastSeen(userId, LocalDateTime.now());
                offlineUserIds.add(userId);
            }
          }
        }
        return offlineUserIds;
    }
}
