package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.dto.response.UserProfileResponse;


import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

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
    private int selfDestructSeconds;
    private Set<UserProfileResponse> members;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
