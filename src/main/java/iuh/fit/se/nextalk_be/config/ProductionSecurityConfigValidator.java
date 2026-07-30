package iuh.fit.se.nextalk_be.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class ProductionSecurityConfigValidator {
    private final Environment environment;

    @PostConstruct
    public void validate() {
        if (!environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            return;
        }
        rejectWildcard("app.websocket.allowed-origin-patterns");
        rejectWildcard("app.cors.allowed-origins");
        requireSecret("app.token.pepper", 32);
        requireDistinct("app.token.pepper", "app.jwt.secret");
        requireExplicitFileScanPolicy();
        requireFalse("app.file-security.direct-upload-enabled");
        requireTrue("app.file-security.private-migration-complete");
    }

    private void rejectWildcard(String propertyName) {
        String value = environment.getProperty(propertyName, "");
        boolean invalid = value.isBlank() || Arrays.stream(value.split(","))
                .map(String::trim)
                .anyMatch(origin -> origin.contains("*"));
        if (invalid) {
            throw new IllegalStateException(
                    propertyName + " must contain an explicit origin allowlist in production");
        }
    }

    private void requireSecret(String propertyName, int minimumBytes) {
        String value = environment.getProperty(propertyName, "");
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < minimumBytes) {
            throw new IllegalStateException(
                    propertyName + " must contain at least " + minimumBytes + " bytes in production");
        }
    }

    private void requireDistinct(String firstProperty, String secondProperty) {
        String first = environment.getProperty(firstProperty, "");
        String second = environment.getProperty(secondProperty, "");
        if (!first.isBlank() && first.equals(second)) {
            throw new IllegalStateException(
                    firstProperty + " must use a different secret from " + secondProperty + " in production");
        }
    }

    private void requireExplicitFileScanPolicy() {
        boolean scannerEnabled =
                environment.getProperty("app.file-security.malware-scanner.enabled", Boolean.class, false);
        boolean basicModeAcknowledged =
                environment.getProperty("app.file-security.allow-basic-unscanned-uploads", Boolean.class, false);
        if (!scannerEnabled && !basicModeAcknowledged) {
            throw new IllegalStateException(
                    "Production must enable malware scanning or explicitly allow restricted basic uploads");
        }
    }

    private void requireTrue(String propertyName) {
        if (!environment.getProperty(propertyName, Boolean.class, false)) {
            throw new IllegalStateException(propertyName + " must be true in production");
        }
    }

    private void requireFalse(String propertyName) {
        if (environment.getProperty(propertyName, Boolean.class, false)) {
            throw new IllegalStateException(propertyName + " must be false in production");
        }
    }
}
