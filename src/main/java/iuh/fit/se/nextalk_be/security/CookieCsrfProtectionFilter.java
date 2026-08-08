package iuh.fit.se.nextalk_be.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@Component
public class CookieCsrfProtectionFilter extends OncePerRequestFilter {
    private static final String REFRESH_COOKIE = "nextalk_refresh";

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private List<String> allowedOrigins;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!requiresProtection(request) || !hasRefreshCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestOrigin = request.getHeader("Origin");
        if (requestOrigin == null || requestOrigin.isBlank()) {
            requestOrigin = originOf(request.getHeader("Referer"));
        }
        final String resolvedOrigin = requestOrigin;
        if (resolvedOrigin == null || allowedOrigins.stream().noneMatch(origin -> sameOrigin(origin, resolvedOrigin))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-site cookie request rejected");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresProtection(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        if ("mobile".equalsIgnoreCase(request.getHeader("X-Client-Platform"))) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/auth/refresh".equals(path) || "/api/auth/logout".equals(path);
    }

    private boolean hasRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean sameOrigin(String configuredOrigin, String requestOrigin) {
        String normalizedConfigured = originOf(configuredOrigin);
        String normalizedRequest = originOf(requestOrigin);
        return normalizedConfigured != null && normalizedConfigured.equalsIgnoreCase(normalizedRequest);
    }

    private String originOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            int port = uri.getPort();
            boolean defaultPort = port == -1
                    || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                    || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
            return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase()
                    + (defaultPort ? "" : ":" + port);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
