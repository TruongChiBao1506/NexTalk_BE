package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private static final int MAX_TRACKED_KEYS = 25_000;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public void check(String scope, String identity, int maxRequests, Duration window) {
        if (maxRequests <= 0 || window == null || window.isNegative() || window.isZero()) {
            return;
        }

        String safeIdentity = normalizeIdentity(identity);
        String key = scope + ":" + safeIdentity;
        long now = System.currentTimeMillis();
        long cutoff = now - window.toMillis();

        Deque<Long> timestamps = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.removeFirst();
            }

            if (timestamps.size() >= maxRequests) {
                throw new RateLimitExceededException("Too many requests. Please wait a moment and try again.");
            }

            timestamps.addLast(now);
        }

        cleanupIfNeeded(cutoff);
    }

    public String clientIdentity(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    public String currentUserIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String normalizeIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            return "unknown";
        }
        return identity.trim().toLowerCase(Locale.ROOT);
    }

    private void cleanupIfNeeded(long cutoff) {
        if (buckets.size() <= MAX_TRACKED_KEYS) {
            return;
        }

        for (Map.Entry<String, Deque<Long>> entry : buckets.entrySet()) {
            if (buckets.size() <= MAX_TRACKED_KEYS) {
                return;
            }
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                    timestamps.removeFirst();
                }
                if (timestamps.isEmpty()) {
                    buckets.remove(entry.getKey(), timestamps);
                }
            }
        }
    }
}
