package iuh.fit.se.nextalk_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDeliveryParticipantResponse {
    private String userId;
    private String username;
    private String avatarUrl;
    private String status;
    private LocalDateTime updatedAt;
}
