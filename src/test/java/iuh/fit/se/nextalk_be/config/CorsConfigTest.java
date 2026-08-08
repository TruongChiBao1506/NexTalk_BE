package iuh.fit.se.nextalk_be.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorsConfigTest {

    private CorsConfigurationSource source;

    @BeforeEach
    void setUp() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(
                config,
                "allowedOrigins",
                List.of("https://nextalk.pages.dev"));
        ReflectionTestUtils.setField(
                config,
                "renderExternalUrl",
                "https://nextalk-be.onrender.com/");
        source = config.corsConfigurationSource();
    }

    @Test
    void renderSelfOriginIsAllowedForSockJsHandshake() {
        CorsConfiguration configuration = configurationFor("/ws/info");

        assertEquals(
                "https://nextalk-be.onrender.com",
                configuration.checkOrigin("https://nextalk-be.onrender.com"));
        assertEquals(
                "https://nextalk.pages.dev",
                configuration.checkOrigin("https://nextalk.pages.dev"));
    }

    @Test
    void renderSelfOriginIsAllowedForRawWebSocketHandshake() {
        CorsConfiguration configuration = configurationFor("/ws-raw");

        assertEquals(
                "https://nextalk-be.onrender.com",
                configuration.checkOrigin("https://nextalk-be.onrender.com"));
    }

    @Test
    void renderSelfOriginIsNotAddedToRestApiCors() {
        CorsConfiguration configuration = configurationFor("/api/health");

        assertNull(configuration.checkOrigin("https://nextalk-be.onrender.com"));
        assertEquals(
                "https://nextalk.pages.dev",
                configuration.checkOrigin("https://nextalk.pages.dev"));
    }

    @Test
    void restApiExposesRetryAfterForRateLimitRecovery() {
        CorsConfiguration configuration = configurationFor("/api/auth/refresh");

        assertEquals(
                List.of("Retry-After", "X-Correlation-Id"),
                configuration.getExposedHeaders());
    }

    private CorsConfiguration configurationFor(String path) {
        HttpServletRequest request = new MockHttpServletRequest("GET", path);
        CorsConfiguration configuration = source.getCorsConfiguration(request);
        assertNotNull(configuration);
        return configuration;
    }
}
