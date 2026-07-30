package iuh.fit.se.nextalk_be.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecureTokenServiceTest {
    private final SecureTokenService service =
            new SecureTokenService("12345678901234567890123456789012");

    @Test
    void generatesUnique256BitOpaqueTokensAndNeverUsesRawValueAsDigest() {
        String first = service.generate();
        String second = service.generate();

        assertNotEquals(first, second);
        assertEquals(32, java.util.Base64.getUrlDecoder().decode(first).length);
        assertNotEquals(first, service.digest(first));
        assertEquals(service.digest(first), service.digest(first));
    }

    @Test
    void rejectsShortPepper() {
        assertThrows(IllegalStateException.class, () -> new SecureTokenService("short"));
    }
}
