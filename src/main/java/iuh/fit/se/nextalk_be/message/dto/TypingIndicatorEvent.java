package iuh.fit.se.nextalk_be.message.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicatorEvent {
    private String type;
    private String conversationId;
    private String userId;
    private String username;
    private boolean typing;
    private LocalDateTime updatedAt;
}
