package iuh.fit.se.nextalk_be.config;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.RefreshTokenRepository;
import iuh.fit.se.nextalk_be.security.CachingUserDetailsService;
import iuh.fit.se.nextalk_be.security.JwtService;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.security.WebSocketSubscriptionAuthorizer;
import iuh.fit.se.nextalk_be.service.SessionRevocationService;
import iuh.fit.se.nextalk_be.service.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketLockedAccountSecurityTest {

    @Test
    void lockedAccountCannotOpenNewStompConnection() {
        JwtService jwtService = mock(JwtService.class);
        CachingUserDetailsService users = mock(CachingUserDetailsService.class);
        SessionRevocationService revocations = mock(SessionRevocationService.class);
        WebSocketConfig config = new WebSocketConfig(
                jwtService,
                users,
                mock(RefreshTokenRepository.class),
                mock(WebSocketSessionRegistry.class),
                mock(WebSocketSubscriptionAuthorizer.class),
                mock(RateLimitService.class),
                revocations);
        User lockedUser = User.builder()
                .email("locked@example.test")
                .username("locked")
                .isAccountLocked(true)
                .build();
        lockedUser.setId("user-1");
        when(jwtService.extractUsername("access-token")).thenReturn(lockedUser.getEmail());
        when(users.loadUserByUsername(lockedUser.getEmail())).thenReturn(lockedUser);

        assertThrows(
                MessageDeliveryException.class,
                () -> ReflectionTestUtils.invokeMethod(config, "buildAuthentication", "access-token"));
        verify(revocations).revokeAllForUser(lockedUser);
    }
}
