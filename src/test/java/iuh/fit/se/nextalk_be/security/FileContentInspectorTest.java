package iuh.fit.se.nextalk_be.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileContentInspectorTest {
    private final FileContentInspector inspector = new FileContentInspector();

    @Test
    void acceptsMatchingMagicBytes() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1};
        assertDoesNotThrow(() -> inspector.validate(png, "image/png", "photo.png"));
    }

    @Test
    void rejectsSpoofedAndMacroEnabledFiles() {
        assertThrows(IllegalArgumentException.class,
                () -> inspector.validate("not png".getBytes(), "image/png", "photo.png"));
        assertThrows(IllegalArgumentException.class,
                () -> inspector.validate(new byte[]{0x50, 0x4B, 3, 4}, "application/zip", "report.docm"));
    }

    @Test
    void basicModeAllowsMediaButRejectsDocumentsAndArchives() {
        assertDoesNotThrow(() -> inspector.validateBasicMode("image/png"));
        assertDoesNotThrow(() -> inspector.validateBasicMode("audio/mpeg"));
        assertThrows(IllegalArgumentException.class,
                () -> inspector.validateBasicMode("application/pdf"));
        assertThrows(IllegalArgumentException.class,
                () -> inspector.validateBasicMode("application/zip"));
        assertThrows(IllegalArgumentException.class,
                () -> inspector.validateBasicMode(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }
}
