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
        redisTemplate.opsForSet().add(sessionsKey, sessionId);

        String currentStatus = getUserStatus(userId);
        if (!"ONLINE".equalsIgnoreCase(currentStatus) && !"AWAY".equalsIgnoreCase(currentStatus)) {
            setUserStatus(userId, "ONLINE");
        }
    }

    public void removeSession(String userId, String sessionId) {
        String sessionsKey = SESSIONS_KEY_PREFIX + userId;
        redisTemplate.opsForSet().remove(sessionsKey, sessionId);

        Long count = redisTemplate.opsForSet().size(sessionsKey);
        if (count == null || count == 0) {
            setUserStatus(userId, "OFFLINE");
            setLastSeen(userId, LocalDateTime.now());
        }
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
        // Fallback to MongoDB
        return userRepository.findById(userId)
                .map(User::getStatus)
                .orElse("OFFLINE");
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
}
