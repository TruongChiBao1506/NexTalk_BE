package iuh.fit.se.nextalk_be.chatrequest.dto;

import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "Message is required")
    private String message;
}
