package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSourceMessageResponse {
    private String messageId;
    private String conversationId;
    private String channelId;
    private String senderId;
    private String senderUsername;
    private String preview;
    private LocalDateTime createdAt;
}
