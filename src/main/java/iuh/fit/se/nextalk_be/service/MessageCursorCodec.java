package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.exception.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

public final class MessageCursorCodec {
    private static final int MAX_CURSOR_LENGTH = 512;
    private static final int MAX_MESSAGE_ID_LENGTH = 128;

    private MessageCursorCodec() {
    }

    public static String encode(LocalDateTime createdAt, String messageId) {
        if (createdAt == null || messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Message cursor requires a timestamp and message ID");
        }
        String value = createdAt + "\n" + messageId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        if (cursor.length() > MAX_CURSOR_LENGTH) {
            throw invalidCursor();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = decoded.indexOf('\n');
            if (separator <= 0 || separator == decoded.length() - 1) {
                throw invalidCursor();
            }
            String messageId = decoded.substring(separator + 1);
            if (messageId.length() > MAX_MESSAGE_ID_LENGTH || messageId.chars().anyMatch(Character::isWhitespace)) {
                throw invalidCursor();
            }
            return new Cursor(LocalDateTime.parse(decoded.substring(0, separator)), messageId);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalidCursor();
        }
    }

    private static BadRequestException invalidCursor() {
        return new BadRequestException("Invalid message cursor");
    }

    public record Cursor(LocalDateTime createdAt, String messageId) {
    }
}
