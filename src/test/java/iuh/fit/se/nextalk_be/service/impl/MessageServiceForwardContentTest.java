package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.ShareMessageRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageServiceForwardContentTest {

    @Test
    void combinesAccompanyingTextBeforeOriginalContent() {
        assertThat(MessageServiceImpl.combineForwardContent("  Xem giúp mình  ", "https://example.com/video"))
                .isEqualTo("Xem giúp mình\n\nhttps://example.com/video");
    }

    @Test
    void keepsOriginalContentWhenAccompanyingTextIsBlank() {
        assertThat(MessageServiceImpl.combineForwardContent("   ", "Nội dung gốc"))
                .isEqualTo("Nội dung gốc");
    }

    @Test
    void rejectsAccompanyingTextLongerThanLimit() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            ShareMessageRequest request = ShareMessageRequest.builder()
                    .targetConversationIds(List.of("conversation-1"))
                    .accompanyingText("a".repeat(1001))
                    .build();

            assertThat(validator.validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("accompanyingText");
        }
    }

    @Test
    void batchRecallDoesNotRequireMongoTransactions() throws NoSuchMethodException {
        var method = MessageServiceImpl.class.getMethod("recallMessages", List.class);

        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }
}
