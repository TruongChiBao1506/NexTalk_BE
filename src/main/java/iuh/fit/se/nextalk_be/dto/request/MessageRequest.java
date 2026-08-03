package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;


import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    @NotBlank(message = "Conversation ID is required")
    @Size(max = 100, message = "Conversation ID must not exceed 100 characters")
    private String conversationId;

    @Size(max = 20000, message = "Message content must not exceed 20000 characters")
    private String content;

    @Size(max = 20, message = "Message type must not exceed 20 characters")
    private String messageType; // TEXT, IMAGE, VIDEO, FILE, ALBUM

    @Valid
    @Size(max = 10, message = "A message may contain at most 10 attachments")
    private List<MessageAttachment> attachments;

    @Size(max = 100, message = "Parent message ID must not exceed 100 characters")
    private String parentId;

    @Pattern(regexp = "(?i)IMPORTANT|URGENT", message = "Priority must be IMPORTANT or URGENT")
    private String priority; // IMPORTANT, URGENT

    @Size(max = 100, message = "Client message ID must not exceed 100 characters")
    private String clientMessageId;

    @Min(value = 1, message = "Self-destruct duration must be positive")
    @Max(value = 2592000, message = "Self-destruct duration must not exceed 30 days")
    private Integer selfDestructSeconds;

    @Size(max = 8, message = "Message metadata contains too many fields")
    private java.util.Map<String, Object> metadata;
}
