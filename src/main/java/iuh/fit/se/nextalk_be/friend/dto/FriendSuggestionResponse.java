package iuh.fit.se.nextalk_be.friend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendSuggestionResponse {
    private String id;
    private String username;
    private String email;
    private String avatarUrl;
    private String bio;
    private String status;
    private LocalDateTime lastSeen;
    private int mutualFriendsCount;
    private boolean isRequestSent;
}
