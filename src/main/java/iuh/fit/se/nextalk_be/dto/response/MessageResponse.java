package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.dto.response.MessageStatusResponse;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import iuh.fit.se.nextalk_be.entity.MessageReaction;


import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private List<MessageAttachment> attachments;
    private LocalDateTime createdAt;
    private List<MessageStatusResponse> statuses;
    private String parentId;
    private String forwardedFromMessageId;
    private String forwardedFromSenderUsername;
    @com.fasterxml.jackson.annotation.JsonProperty("isEdited")
    private boolean isEdited;
    private LocalDateTime editedAt;
    @com.fasterxml.jackson.annotation.JsonProperty("isRecalled")
    private boolean isRecalled;
    @com.fasterxml.jackson.annotation.JsonProperty("isPinned")
    private boolean isPinned;
    private LocalDateTime pinnedAt;
    private LocalDateTime expiresAt;
    private List<MessageReaction> reactions;
    private Map<String, Object> metadata;
}
