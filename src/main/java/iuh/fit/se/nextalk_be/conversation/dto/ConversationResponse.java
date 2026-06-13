package iuh.fit.se.nextalk_be.conversation.dto;

import iuh.fit.se.nextalk_be.user.dto.UserProfileResponse;
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
    private int selfDestructSeconds;
    private Set<UserProfileResponse> members;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
