package iuh.fit.se.nextalk_be.presence.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceUpdateResponse {
    private UUID userId;
    private String username;
    private String status; // ONLINE, AWAY, OFFLINE
    private LocalDateTime lastSeen;
}
