package iuh.fit.se.nextalk_be.message.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private String id;
    private String conversationId;
    private String senderId;
    private String senderUsername;
    private String content;
    private String messageType;
    private LocalDateTime createdAt;
    private List<MessageStatusResponse> statuses;
    private String parentId;
    @com.fasterxml.jackson.annotation.JsonProperty("isEdited")
    private boolean isEdited;
    private LocalDateTime editedAt;
    @com.fasterxml.jackson.annotation.JsonProperty("isRecalled")
    private boolean isRecalled;
    @com.fasterxml.jackson.annotation.JsonProperty("isPinned")
    private boolean isPinned;
    private List<iuh.fit.se.nextalk_be.message.MessageReaction> reactions;
}
