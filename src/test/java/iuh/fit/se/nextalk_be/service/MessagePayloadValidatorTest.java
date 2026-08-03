package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.MessageRequest;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessagePayloadValidatorTest {

    private final MessagePayloadValidator payloadValidator = new MessagePayloadValidator();

    @Test
    void rejectsServerOwnedMetadataFields() {
        MessageRequest request = MessageRequest.builder()
                .conversationId("conversation-id")
                .content("payload")
                .metadata(Map.of("systemType", "FORGED"))
                .build();

        assertThatThrownBy(() -> payloadValidator.validate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unsupported message metadata fields");
    }

    @Test
    void rejectsOversizedNestedMetadata() {
        MessageRequest request = MessageRequest.builder()
                .conversationId("conversation-id")
                .content("payload")
                .metadata(Map.of("gif", Map.of("altText", "x".repeat(17000))))
                .build();

        assertThatThrownBy(() -> payloadValidator.validate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Message metadata is too large");
    }

    @Test
    void beanValidationLimitsMessageContentAndAttachments() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            MessageRequest request = MessageRequest.builder()
                    .conversationId("conversation-id")
                    .content("x".repeat(20001))
                    .attachments(List.of(
                            new MessageAttachment("url-1", "IMAGE", null, 1L),
                            new MessageAttachment("url-2", "IMAGE", null, 1L),
                            new MessageAttachment("url-3", "IMAGE", null, 1L),
                            new MessageAttachment("url-4", "IMAGE", null, 1L),
                            new MessageAttachment("url-5", "IMAGE", null, 1L),
                            new MessageAttachment("url-6", "IMAGE", null, 1L),
                            new MessageAttachment("url-7", "IMAGE", null, 1L),
                            new MessageAttachment("url-8", "IMAGE", null, 1L),
                            new MessageAttachment("url-9", "IMAGE", null, 1L),
                            new MessageAttachment("url-10", "IMAGE", null, 1L),
                            new MessageAttachment("url-11", "IMAGE", null, 1L)))
                    .build();

            assertThat(validator.validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("content", "attachments");
        }
    }
}
