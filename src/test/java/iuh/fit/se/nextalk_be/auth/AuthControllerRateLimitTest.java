package iuh.fit.se.nextalk_be.auth;

import iuh.fit.se.nextalk_be.controller.AuthController;
import iuh.fit.se.nextalk_be.dto.request.TokenRefreshRequest;
import iuh.fit.se.nextalk_be.dto.response.TokenRefreshResponse;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.security.SecureTokenService;
import iuh.fit.se.nextalk_be.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AuthControllerRateLimitTest {

    @Test
    void refreshUsesTokenScopedLimitBeforeCoarseClientLimit() {
        AuthService authService = mock(AuthService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        SecureTokenService secureTokenService = mock(SecureTokenService.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        AuthController controller = new AuthController(authService, rateLimitService, secureTokenService);
        TokenRefreshRequest request = TokenRefreshRequest.builder()
                .refreshToken("opaque-refresh-token")
                .build();

        when(secureTokenService.digest("opaque-refresh-token")).thenReturn("token-digest");
        when(rateLimitService.clientIdentity(httpRequest)).thenReturn("shared-proxy");
        when(authService.refreshToken(any(TokenRefreshRequest.class), same(httpRequest))).thenReturn(
                TokenRefreshResponse.builder()
                        .accessToken("access-token")
                        .refreshToken("rotated-refresh-token")
                        .build());

        var response = controller.refresh(request, null, httpRequest, "mobile");

        assertTrue(response.getStatusCode().is2xxSuccessful());
        InOrder order = inOrder(rateLimitService);
        order.verify(rateLimitService).check(
                "auth:refresh:token", "token-digest", 20, Duration.ofMinutes(1));
        order.verify(rateLimitService).check(
                "auth:refresh:client", "shared-proxy", 600, Duration.ofMinutes(1));
        verify(authService).refreshToken(
                argThat(resolved -> "opaque-refresh-token".equals(resolved.getRefreshToken())),
                same(httpRequest));
        verify(rateLimitService, never()).check(
                eq("auth:refresh:token"), eq("opaque-refresh-token"), anyInt(), any());
    }
}
