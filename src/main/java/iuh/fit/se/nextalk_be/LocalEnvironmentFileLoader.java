package iuh.fit.se.nextalk_be;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Loads a developer-only .env file without letting it override real process
 * environment variables. Production must receive secrets from its deployment
 * environment and never from a file in the application directory.
 */
final class LocalEnvironmentFileLoader {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

    private LocalEnvironmentFileLoader() {
    }

    static void load(Path startDirectory, Map<String, String> environment, Properties properties) {
        if (isProduction(environment, properties)) return;

        Path envFile = findEnvironmentFile(startDirectory);
        if (envFile == null) return;

        try {
            for (String rawLine : Files.readAllLines(envFile)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int separator = line.indexOf('=');
                if (separator <= 0) continue;

                String key = line.substring(0, separator).trim();
                if (!KEY_PATTERN.matcher(key).matches()
                        || environment.containsKey(key)
                        || properties.containsKey(key)) {
                    continue;
                }

                String value = unquote(line.substring(separator + 1).trim());
                properties.setProperty(key, value);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the local environment file", exception);
        }
    }

    private static boolean isProduction(Map<String, String> environment, Properties properties) {
        String activeProfiles = environment.getOrDefault(
                "SPRING_PROFILES_ACTIVE",
                properties.getProperty("spring.profiles.active", "")
        );
        return Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));
    }

    private static Path findEnvironmentFile(Path startDirectory) {
        Path current = startDirectory == null ? null : startDirectory.toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(".env");
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            List<String> quotePairs = List.of("\"\"", "''");
            String boundaries = value.substring(0, 1) + value.substring(value.length() - 1);
            if (quotePairs.contains(boundaries)) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
