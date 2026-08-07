package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.dto.request.MessageRequest;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.service.MessagePayloadValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageEffectTest {

    private MessagePayloadValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MessagePayloadValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = { "GIFT", "FIRE", "BALLOON", "HEART" })
    void validate_WithValidEffectMetadata_ShouldPass(String effect) {
        MessageRequest request = MessageRequest.builder()
                .conversationId("conv-123")
                .content("Happy Birthday!")
                .metadata(Map.of("effect", effect))
                .build();

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void validate_WithUnknownEffect_ShouldThrowException() {
        MessageRequest request = MessageRequest.builder()
                .conversationId("conv-123")
                .content("Hello")
                .metadata(Map.of("effect", "UNKNOWN"))
                .build();

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void validate_WithNonStringEffect_ShouldThrowException() {
        MessageRequest request = MessageRequest.builder()
                .conversationId("conv-123")
                .content("Hello")
                .metadata(Map.of("effect", 1))
                .build();

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void validate_WithUnsupportedMetadataKey_ShouldThrowException() {
        MessageRequest request = MessageRequest.builder()
                .conversationId("conv-123")
                .content("Hello")
                .metadata(Map.of("invalidKey", "value"))
                .build();

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }
}
