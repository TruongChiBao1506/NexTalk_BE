package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.entity.GroupEventRsvpStatus;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupEventParticipantResponse {
    private String userId;
    private String username;
    private String avatarUrl;
    private GroupEventRsvpStatus status;
}
