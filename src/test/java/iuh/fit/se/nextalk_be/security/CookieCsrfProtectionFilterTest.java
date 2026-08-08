package iuh.fit.se.nextalk_be.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CookieCsrfProtectionFilterTest {
    private CookieCsrfProtectionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CookieCsrfProtectionFilter();
        ReflectionTestUtils.setField(filter, "allowedOrigins", List.of("https://app.example.test"));
    }

    @Test
    void crossSiteCookieRefreshIsRejected() throws Exception {
        MockHttpServletRequest request = cookieRequest("/api/auth/refresh");
        request.addHeader("Origin", "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void allowedOriginCookieRefreshPasses() throws Exception {
        MockHttpServletRequest request = cookieRequest("/api/auth/refresh");
        request.addHeader("Origin", "https://app.example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void mobileBodyTokenRefreshIsNotBlockedByStaleNativeCookie() throws Exception {
        MockHttpServletRequest request = cookieRequest("/api/auth/refresh");
        request.addHeader("X-Client-Platform", "mobile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    private MockHttpServletRequest cookieRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setCookies(new Cookie("nextalk_refresh", "opaque-test-value"));
        return request;
    }
}
