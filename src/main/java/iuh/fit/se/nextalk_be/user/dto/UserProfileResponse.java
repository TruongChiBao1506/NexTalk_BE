package iuh.fit.se.nextalk_be.user.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID id;
    private String email;
    private String username;
    private String avatarUrl;
    private String bio;
    private String status;
    private LocalDateTime lastSeen;
    @com.fasterxml.jackson.annotation.JsonProperty("isVerified")
    private boolean isVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
