package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReminderResponse {
    private String id;
    private String messageId;
    private String conversationId;
    private String senderUsername;
    private String messagePreview;
    private String remindAt;
    private String note;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime firedAt;
    private LocalDateTime deletedAt;
}
