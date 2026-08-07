package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.dto.response.UserProfileResponse;


import lombok.*;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Set;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private String id;
    private String type;
    private String name;
    private boolean canSendMessages;
    private boolean blockedByMe;
    private boolean blockedMe;
    private boolean pinned;
    private boolean hidden;
    private boolean muted;
    private String notificationMode;
    private Instant mutedUntil;
    private int selfDestructSeconds;
    private Set<UserProfileResponse> members;
    private String themeColor;
    private String wallpaperUrl;
    private Map<String, String> nicknames;
    private java.util.List<iuh.fit.se.nextalk_be.entity.Conversation.WordEffectItem> wordEffects;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
