package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusUpdateResponse {
    @Builder.Default
    private String type = "STATUS_UPDATE";
    private String conversationId;
    private String userId;
    private String username;
    private String status; // DELIVERED, SEEN
    private LocalDateTime updatedAt;
}
