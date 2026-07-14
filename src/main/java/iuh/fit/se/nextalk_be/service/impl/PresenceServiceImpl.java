package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.PresenceService;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.UserRepository;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    private static final String SESSIONS_KEY_PREFIX = "nextalk:presence:sessions:";
    private static final String STATUS_KEY_PREFIX = "nextalk:presence:status:";
    private static final String LAST_SEEN_KEY_PREFIX = "nextalk:presence:last_seen:";

    @jakarta.annotation.PostConstruct
    public void clearAllSessionsOnStartup() {
        try {
            java.util.Set<String> keys = redisTemplate.keys(SESSIONS_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            java.util.Set<String> statusKeys = redisTemplate.keys(STATUS_KEY_PREFIX + "*");
            if (statusKeys != null && !statusKeys.isEmpty()) {
                redisTemplate.delete(statusKeys);
            }
        } catch (Exception e) {
            // Ignore Redis errors during startup
        }
    }

    public void addSession(String userId, String sessionId) {
        String sessionsKey = SESSIONS_KEY_PREFIX + userId;
        redisTemplate.opsForHash().put(sessionsKey, sessionId, String.valueOf(System.currentTimeMillis()));

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
        redisTemplate.opsForHash().put(
                SESSIONS_KEY_PREFIX + userId,
                sessionId,
                String.valueOf(System.currentTimeMillis())
        );
    }

    public List<String> expireStaleSessions(long staleBeforeEpochMillis) {
        List<String> offlineUserIds = new ArrayList<>();
        java.util.Set<String> keys = redisTemplate.keys(SESSIONS_KEY_PREFIX + "*");
        if (keys == null) return offlineUserIds;

        for (String key : keys) {
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
        return offlineUserIds;
    }
}
