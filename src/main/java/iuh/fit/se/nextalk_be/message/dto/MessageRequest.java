package iuh.fit.se.nextalk_be.message.dto;

import jakarta.validation.constraints.NotNull;
import iuh.fit.se.nextalk_be.message.MessageAttachment;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    @NotNull(message = "Conversation ID is required")
    private String conversationId;

    private String content;

    private String messageType; // TEXT, IMAGE, VIDEO, FILE, ALBUM

    private List<MessageAttachment> attachments;

    private String parentId;
}
