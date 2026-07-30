package iuh.fit.se.nextalk_be.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class OriginAllowlist {

    private OriginAllowlist() {
    }

    static List<String> merge(String[] configuredOrigins, String runtimeOrigin) {
        Collection<String> configured = configuredOrigins == null
                ? List.of()
                : Arrays.asList(configuredOrigins);
        return merge(configured, runtimeOrigin);
    }

    static List<String> merge(Collection<String> configuredOrigins, String runtimeOrigin) {
        Set<String> origins = new LinkedHashSet<>();
        if (configuredOrigins != null) {
            configuredOrigins.stream()
                    .filter(origin -> origin != null && !origin.isBlank())
                    .map(String::trim)
                    .forEach(origins::add);
        }
        normalizeHttpOrigin(runtimeOrigin).ifPresent(origins::add);
        return List.copyOf(origins);
    }

    private static Optional<String> normalizeHttpOrigin(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(candidate.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!"http".equals(scheme) && !"https".equals(scheme))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                return Optional.empty();
            }
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            return Optional.of(
                    scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + port);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
