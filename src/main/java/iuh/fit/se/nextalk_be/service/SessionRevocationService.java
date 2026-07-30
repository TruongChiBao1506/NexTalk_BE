package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.RefreshToken;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.RefreshTokenRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionRevocationService {
    public static final String REVOCATION_CHANNEL = "nextalk:security:session-revoked";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final StringRedisTemplate redisTemplate;

    public void revokeAllForUser(User user) {
        List<RefreshToken> sessions = refreshTokenRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<String> sessionIds = sessions.stream()
                .map(RefreshToken::getId)
                .filter(java.util.Objects::nonNull)
                .toList();

        refreshTokenRepository.deleteByUser(user);
        webSocketSessionRegistry.closeLoginSessions(sessionIds);
        publish(sessionIds);

        if (user.getFcmTokens() != null && !user.getFcmTokens().isEmpty()) {
            user.setFcmTokens(new ArrayList<>());
            userRepository.save(user);
        }
    }

    public void closeSessionsFromRemoteEvent(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        for (String sessionId : payload.split(",")) {
            if (!sessionId.isBlank()) {
                webSocketSessionRegistry.closeLoginSession(sessionId.trim());
            }
        }
    }

    private void publish(List<String> sessionIds) {
        if (sessionIds.isEmpty()) {
            return;
        }
        try {
            redisTemplate.convertAndSend(REVOCATION_CHANNEL, String.join(",", sessionIds));
        } catch (Exception ignored) {
            // Database revocation remains authoritative; Redis only accelerates
            // closing sockets connected to other backend instances.
        }
    }
}
