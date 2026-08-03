package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GiphyMessageMetadataValidatorTest {
    private final GiphyMessageMetadataValidator validator = new GiphyMessageMetadataValidator();

    @Test
    void keepsOnlyIdentifierAndAttributionMetadata() {
        Map<String, Object> result = validator.sanitize("abc123", Map.of("gif", Map.of(
                "provider", "GIPHY", "id", "abc123", "title", "Hello", "username", "artist",
                "giphyUrl", "https://giphy.com/gifs/abc123", "mediaUrl", "https://media.giphy.com/media/abc123/giphy.gif",
                "analytics", Map.of("onload", "https://pingback.giphy.com/x")
        )));
        Map<?, ?> gif = (Map<?, ?>) result.get("gif");
        assertEquals("GIPHY", gif.get("provider"));
        assertEquals("abc123", gif.get("id"));
        assertEquals("Hello", gif.get("title"));
        assertFalse(gif.containsKey("mediaUrl"));
        assertFalse(gif.containsKey("analytics"));
    }

    @Test
    void rejectsMismatchedIdentifier() {
        assertThrows(BadRequestException.class, () -> validator.sanitize("abc123", Map.of(
                "gif", Map.of("provider", "GIPHY", "id", "different")
        )));
    }

    @Test
    void rejectsNonGiphyAttributionUrl() {
        assertThrows(BadRequestException.class, () -> validator.sanitize("abc123", Map.of(
                "gif", Map.of("provider", "GIPHY", "id", "abc123", "profileUrl", "https://example.com/profile")
        )));
    }
}
