package iuh.fit.se.nextalk_be.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.dto.request.MessageRequest;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class MessagePayloadValidator {
    static final int MAX_METADATA_BYTES = 16 * 1024;
    static final Set<String> ALLOWED_MESSAGE_EFFECTS = Set.of(
            "GIFT",
            "FIRE",
            "BALLOON",
            "HEART"
    );
    static final Set<String> ALLOWED_CLIENT_METADATA_KEYS = Set.of(
            "clientMessageId",
            "effect",
            "gif",
            "priority",
            "selfDestructSeconds",
            "silent",
            "suppressLinkPreview"
    );

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public void validate(MessageRequest request) {
        Map<String, Object> metadata = request.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return;
        }

        Set<String> unsupportedKeys = new LinkedHashSet<>(metadata.keySet());
        unsupportedKeys.removeAll(ALLOWED_CLIENT_METADATA_KEYS);
        if (!unsupportedKeys.isEmpty()) {
            throw new BadRequestException("Unsupported message metadata fields");
        }

        Object effect = metadata.get("effect");
        if (effect != null && (!(effect instanceof String effectName)
                || !ALLOWED_MESSAGE_EFFECTS.contains(effectName))) {
            throw new BadRequestException("Unsupported message effect");
        }

        try {
            if (OBJECT_MAPPER.writeValueAsBytes(metadata).length > MAX_METADATA_BYTES) {
                throw new BadRequestException("Message metadata is too large");
            }
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Message metadata is invalid");
        }
    }
}
