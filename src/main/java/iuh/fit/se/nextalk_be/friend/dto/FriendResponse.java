package iuh.fit.se.nextalk_be.friend.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendResponse {
    private String id;
    private String email;
    private String username;
    private String avatarUrl;
    private String bio;
    private String status;
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;
}
