package iuh.fit.se.nextalk_be.chatrequest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChatRequest {
    @NotNull(message = "Receiver ID is required")
    private String receiverId;

    private String message;

    private String sharedMessageId;
}
