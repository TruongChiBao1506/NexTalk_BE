package iuh.fit.se.nextalk_be.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPushPayload {
    private NotificationPushKind kind;
    private String title;
    private String body;
    private String messageId;
    private String conversationId;
    private String conversationName;
    private String senderId;
    private String senderName;
    private String senderAvatarUrl;
}
