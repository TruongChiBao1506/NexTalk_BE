package iuh.fit.se.nextalk_be.entity;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSourceMessage {
    private String messageId;
    private String conversationId;
    private String channelId;
    private String senderId;
    private String senderUsername;
    private String preview;
    private LocalDateTime createdAt;
}
