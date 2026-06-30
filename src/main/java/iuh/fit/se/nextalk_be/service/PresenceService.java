package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public interface PresenceService {
    @jakarta.annotation.PostConstruct public void clearAllSessionsOnStartup();
    public void addSession(String userId, String sessionId);
    public void removeSession(String userId, String sessionId);
    public void setUserStatus(String userId, String status);
    public String getUserStatus(String userId);
    public void setLastSeen(String userId, LocalDateTime lastSeenTime);
    public LocalDateTime getUserLastSeen(String userId);
}
