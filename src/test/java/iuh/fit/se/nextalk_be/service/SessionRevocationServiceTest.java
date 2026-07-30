package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.RefreshToken;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.RefreshTokenRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionRevocationServiceTest {

    @Test
    void revocationDeletesSessionsClosesSocketsAndClearsPushTokens() {
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        UserRepository users = mock(UserRepository.class);
        WebSocketSessionRegistry sockets = mock(WebSocketSessionRegistry.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SessionRevocationService service = new SessionRevocationService(tokens, users, sockets, redis);

        User user = User.builder()
                .email("locked@example.test")
                .username("locked")
                .fcmTokens(new ArrayList<>(List.of("push-token")))
                .build();
        user.setId("user-1");
        RefreshToken session = RefreshToken.builder().user(user).build();
        session.setId("session-1");
        when(tokens.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of(session));

        service.revokeAllForUser(user);

        verify(tokens).deleteByUser(user);
        verify(sockets).closeLoginSessions(List.of("session-1"));
        verify(redis).convertAndSend(SessionRevocationService.REVOCATION_CHANNEL, "session-1");
        verify(users).save(user);
        assertTrue(user.getFcmTokens().isEmpty());
    }

    @Test
    void remoteRevocationEventClosesEveryListedLocalSocket() {
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        UserRepository users = mock(UserRepository.class);
        WebSocketSessionRegistry sockets = mock(WebSocketSessionRegistry.class);
        SessionRevocationService service = new SessionRevocationService(
                tokens, users, sockets, mock(StringRedisTemplate.class));

        service.closeSessionsFromRemoteEvent("session-1,session-2");

        verify(sockets).closeLoginSession("session-1");
        verify(sockets).closeLoginSession("session-2");
    }
}
