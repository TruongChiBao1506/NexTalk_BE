package iuh.fit.se.nextalk_be.message.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusUpdateResponse {
    @Builder.Default
    private String type = "STATUS_UPDATE";
    private UUID conversationId;
    private UUID userId;
    private String username;
    private String status; // DELIVERED, SEEN
    private LocalDateTime updatedAt;
}
