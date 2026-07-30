package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;


import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User extends BaseEntity implements UserDetails {

    @Indexed(unique = true)
    private String email;

    @Indexed(unique = true)
    private String username;

    private String password;

    private String avatarUrl;

    private String bio;

    @Indexed(unique = true, sparse = true)
    private String profileQrToken;

    @Builder.Default
    private Boolean profileQrEnabled = true;

    public boolean isProfileQrEnabled() {
        return profileQrEnabled == null || profileQrEnabled;
    }

    private String status; // ONLINE, OFFLINE, AWAY

    @Builder.Default
    private Boolean showActivityStatus = true;

    public boolean isShowActivityStatus() {
        return showActivityStatus == null || showActivityStatus;
    }

    @Builder.Default
    private Boolean blockStrangerMessages = false;

    public boolean isBlockStrangerMessages() {
        return Boolean.TRUE.equals(blockStrangerMessages);
    }

    @Builder.Default
    private Boolean friendSuggestionDiscoverable = true;

    public boolean isFriendSuggestionDiscoverable() {
        return friendSuggestionDiscoverable == null || friendSuggestionDiscoverable;
    }

    @Builder.Default
    private Boolean systemAccount = false;

    public boolean isSystemAccount() {
        if (Boolean.TRUE.equals(systemAccount)) {
            return true;
        }
        if (email != null && "moderator@nextalk.local".equalsIgnoreCase(email.trim())) {
            return true;
        }
        if (username == null) {
            return false;
        }
        String normalizedUsername = username.trim();
        return "NexTalk Moderator".equalsIgnoreCase(normalizedUsername)
                || "NexTalk AI".equalsIgnoreCase(normalizedUsername);
    }

    private String chatPin;

    @Builder.Default
    private int chatPinFailedAttempts = 0;

    private java.time.LocalDateTime chatPinLockedUntil;

    /** Birthday stored as YYYY-MM-dd (e.g. 2000-07-11) */
    private String birthday;

    @Builder.Default
    private boolean enableBirthdayNotification = true;

    @Builder.Default
    private BirthdayVisibility birthdayVisibility = BirthdayVisibility.FRIENDS;

    @Builder.Default
    private boolean birthdayReminderEnabled = true;

    public BirthdayVisibility getBirthdayVisibility() {
        return birthdayVisibility == null ? BirthdayVisibility.FRIENDS : birthdayVisibility;
    }

    @Builder.Default
    private boolean isVerified = false;

    @Builder.Default
    private boolean isAccountLocked = false;

    @Builder.Default
    private UserRole role = UserRole.USER;

    @Builder.Default
    private List<String> fcmTokens = new java.util.ArrayList<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        UserRole effectiveRole = role == null ? UserRole.USER : role;
        return List.of(new SimpleGrantedAuthority("ROLE_" + effectiveRole.name()));
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !isAccountLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
