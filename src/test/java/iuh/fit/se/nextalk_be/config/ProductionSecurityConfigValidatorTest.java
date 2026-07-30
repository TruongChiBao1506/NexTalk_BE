package iuh.fit.se.nextalk_be.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecurityConfigValidatorTest {

    @Test
    void productionRejectsWildcardOrigins() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("app.websocket.allowed-origin-patterns", "*")
                .withProperty("app.cors.allowed-origins", "https://app.example.test")
                .withProperty("app.token.pepper", "12345678901234567890123456789012");
        environment.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class,
                () -> new ProductionSecurityConfigValidator(environment).validate());
    }

    @Test
    void productionAcceptsExplicitOrigins() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.websocket.allowed-origin-patterns", "https://app.example.test")
                .withProperty("app.cors.allowed-origins", "https://app.example.test")
                .withProperty("app.token.pepper", "12345678901234567890123456789012")
                .withProperty("app.jwt.secret", "abcdefghijklmnopqrstuvwxyz-9876543210")
                .withProperty("app.file-security.malware-scanner.enabled", "true")
                .withProperty("app.file-security.direct-upload-enabled", "false")
                .withProperty("app.file-security.private-migration-complete", "true");
        environment.setActiveProfiles("prod");

        assertDoesNotThrow(() -> new ProductionSecurityConfigValidator(environment).validate());
    }

    @Test
    void productionAcceptsExplicitRestrictedBasicUploadMode() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.websocket.allowed-origin-patterns", "https://app.example.test")
                .withProperty("app.cors.allowed-origins", "https://app.example.test")
                .withProperty("app.token.pepper", "12345678901234567890123456789012")
                .withProperty("app.jwt.secret", "abcdefghijklmnopqrstuvwxyz-9876543210")
                .withProperty("app.file-security.malware-scanner.enabled", "false")
                .withProperty("app.file-security.allow-basic-unscanned-uploads", "true")
                .withProperty("app.file-security.direct-upload-enabled", "false")
                .withProperty("app.file-security.private-migration-complete", "true");
        environment.setActiveProfiles("prod");

        assertDoesNotThrow(() -> new ProductionSecurityConfigValidator(environment).validate());
    }

    @Test
    void productionRejectsDisabledScannerWithoutBasicModeAcknowledgement() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.websocket.allowed-origin-patterns", "https://app.example.test")
                .withProperty("app.cors.allowed-origins", "https://app.example.test")
                .withProperty("app.token.pepper", "12345678901234567890123456789012")
                .withProperty("app.jwt.secret", "abcdefghijklmnopqrstuvwxyz-9876543210")
                .withProperty("app.file-security.malware-scanner.enabled", "false");
        environment.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class,
                () -> new ProductionSecurityConfigValidator(environment).validate());
    }

    @Test
    void productionRejectsTokenPepperReusedAsJwtSecret() {
        String reusedSecret = "12345678901234567890123456789012";
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.websocket.allowed-origin-patterns", "https://app.example.test")
                .withProperty("app.cors.allowed-origins", "https://app.example.test")
                .withProperty("app.token.pepper", reusedSecret)
                .withProperty("app.jwt.secret", reusedSecret);
        environment.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class,
                () -> new ProductionSecurityConfigValidator(environment).validate());
    }

    @Test
    void productionRejectsShortTokenPepper() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.websocket.allowed-origin-patterns", "https://app.example.test")
                .withProperty("app.cors.allowed-origins", "https://app.example.test")
                .withProperty("app.token.pepper", "too-short");
        environment.setActiveProfiles("production");

        assertThrows(IllegalStateException.class,
                () -> new ProductionSecurityConfigValidator(environment).validate());
    }
}
