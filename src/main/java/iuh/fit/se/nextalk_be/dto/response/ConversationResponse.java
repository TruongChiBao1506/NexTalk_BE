package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.dto.response.UserProfileResponse;


import lombok.*;

import java.time.LocalDateTime;
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
    private int selfDestructSeconds;
    private Set<UserProfileResponse> members;
    private String themeColor;
    private String wallpaperUrl;
    private Map<String, String> nicknames;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
