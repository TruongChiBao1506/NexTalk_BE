package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledMessageResponse {
    private String id;
    private String conversationId;
    private String content;
    private String scheduledAt;
    private boolean silent;
    private String status;
    private String sentMessageId;
    private LocalDateTime createdAt;
}
