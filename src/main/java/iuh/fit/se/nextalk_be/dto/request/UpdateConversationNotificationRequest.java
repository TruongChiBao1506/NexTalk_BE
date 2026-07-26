package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.ConversationNotificationMode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class UpdateConversationNotificationRequest {
    @NotNull
    private ConversationNotificationMode mode;

    private Instant mutedUntil;
}
