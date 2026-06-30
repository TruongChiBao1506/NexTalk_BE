package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.entity.InvitationStatus;


import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupInvitationResponse {
    private String id;
    private String groupId;
    private String groupName;
    private String groupAvatarUrl;
    private String inviterId;
    private String inviterUsername;
    private String inviteeId;
    private String inviteeUsername;
    private String inviteeAvatarUrl;
    private InvitationStatus status;
    private LocalDateTime createdAt;
}
