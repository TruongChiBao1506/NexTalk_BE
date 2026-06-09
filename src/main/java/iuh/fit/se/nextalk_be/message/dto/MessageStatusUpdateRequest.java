package iuh.fit.se.nextalk_be.message.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusUpdateRequest {
    @NotNull(message = "Conversation ID is required")
    private String conversationId;
}
