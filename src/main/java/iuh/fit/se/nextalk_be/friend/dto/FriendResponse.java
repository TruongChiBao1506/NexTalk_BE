package iuh.fit.se.nextalk_be.friend.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendResponse {
    private UUID id;
    private String email;
    private String username;
    private String avatarUrl;
    private String bio;
    private String status;
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;
}
