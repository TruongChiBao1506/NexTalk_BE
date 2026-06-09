package iuh.fit.se.nextalk_be.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    @NotNull(message = "Conversation ID is required")
    private String conversationId;

    @NotBlank(message = "Message content cannot be blank")
    private String content;

    private String messageType; // TEXT, IMAGE, VIDEO, FILE

    private String parentId;
}
