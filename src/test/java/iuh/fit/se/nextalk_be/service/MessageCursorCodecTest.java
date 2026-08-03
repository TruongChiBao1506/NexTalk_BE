package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageCursorCodecTest {
    @Test
    void roundTripPreservesTimestampAndMessageId() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 30, 15, 123_000_000);
        String encoded = MessageCursorCodec.encode(createdAt, "cursor-message-id");

        MessageCursorCodec.Cursor decoded = MessageCursorCodec.decode(encoded);

        assertEquals(createdAt, decoded.createdAt());
        assertEquals("cursor-message-id", decoded.messageId());
    }

    @Test
    void malformedCursorIsRejected() {
        assertThrows(BadRequestException.class, () -> MessageCursorCodec.decode("not-a-valid-cursor"));
    }
}
