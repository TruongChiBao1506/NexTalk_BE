package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.dto.response.ChannelResponse;
import iuh.fit.se.nextalk_be.dto.response.GroupMemberResponse;


import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    private String id;
    private String name;
    private String avatarUrl;
    private String conversationId;
    private String ownerId;
    private String ownerUsername;
    private List<ChannelResponse> channels;
    private boolean requiresApproval;
    private String inviteCode;
    private int pendingApprovalCount;
    private List<GroupMemberResponse> members;
    private int memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
