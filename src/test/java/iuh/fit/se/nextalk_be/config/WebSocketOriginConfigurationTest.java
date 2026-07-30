package iuh.fit.se.nextalk_be.config;

import iuh.fit.se.nextalk_be.repository.RefreshTokenRepository;
import iuh.fit.se.nextalk_be.security.JwtService;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.security.WebSocketSubscriptionAuthorizer;
import iuh.fit.se.nextalk_be.service.SessionRevocationService;
import iuh.fit.se.nextalk_be.service.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;

class WebSocketOriginConfigurationTest {

    @Test
    void renderExternalUrlIsAutomaticallyAllowedForNativeMobileSockets() {
        WebSocketConfig config = newConfig();
        ReflectionTestUtils.setField(
                config,
                "webSocketAllowedOriginPatterns",
                new String[]{"https://nextalk.pages.dev"});
        ReflectionTestUtils.setField(
                config,
                "renderExternalUrl",
                "https://nextalk-be.onrender.com/");

        String[] origins = ReflectionTestUtils.invokeMethod(config, "resolveAllowedOriginPatterns");

        assertArrayEquals(
                new String[]{
                        "https://nextalk.pages.dev",
                        "https://nextalk-be.onrender.com"
                },
                origins);
    }

    @Test
    void invalidRenderUrlDoesNotBroadenConfiguredOriginAllowlist() {
        WebSocketConfig config = newConfig();
        ReflectionTestUtils.setField(
                config,
                "webSocketAllowedOriginPatterns",
                new String[]{"https://nextalk.pages.dev"});
        ReflectionTestUtils.setField(config, "renderExternalUrl", "javascript:alert(1)");

        String[] origins = ReflectionTestUtils.invokeMethod(config, "resolveAllowedOriginPatterns");

        assertArrayEquals(new String[]{"https://nextalk.pages.dev"}, origins);
    }

    private WebSocketConfig newConfig() {
        return new WebSocketConfig(
                mock(JwtService.class),
                mock(UserDetailsService.class),
                mock(RefreshTokenRepository.class),
                mock(WebSocketSessionRegistry.class),
                mock(WebSocketSubscriptionAuthorizer.class),
                mock(RateLimitService.class),
                mock(SessionRevocationService.class));
    }
}
