package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.entity.BirthdayVisibility;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String id;
    private String email;
    private String username;
    private String avatarUrl;
    private String bio;
    private String status;
    private LocalDateTime lastSeen;
    private boolean showActivityStatus;
    private boolean blockStrangerMessages;
    private boolean friendSuggestionDiscoverable;
    @com.fasterxml.jackson.annotation.JsonProperty("isVerified")
    private boolean isVerified;
    private boolean hasChatPin;
    private String birthday;
    private boolean enableBirthdayNotification;
    private BirthdayVisibility birthdayVisibility;
    private boolean birthdayReminderEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
