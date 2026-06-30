package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceUpdateResponse {
    private String userId;
    private String username;
    private String status; // ONLINE, AWAY, OFFLINE
    private LocalDateTime lastSeen;
}
