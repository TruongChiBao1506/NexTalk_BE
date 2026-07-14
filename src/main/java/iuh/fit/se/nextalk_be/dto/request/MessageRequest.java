package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    private String priority; // IMPORTANT, URGENT

    @Size(max = 100, message = "Client message ID must not exceed 100 characters")
    private String clientMessageId;
}
