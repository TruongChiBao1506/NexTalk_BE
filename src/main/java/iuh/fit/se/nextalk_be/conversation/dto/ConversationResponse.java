package iuh.fit.se.nextalk_be.conversation.dto;

import iuh.fit.se.nextalk_be.user.dto.UserProfileResponse;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private UUID id;
    private String type;
    private String name;
    private Set<UserProfileResponse> members;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
