package iuh.fit.se.nextalk_be.message.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusResponse {
    private String userId;
    private String username;
    private String status; // SENT, DELIVERED, SEEN
    private LocalDateTime updatedAt;
}
