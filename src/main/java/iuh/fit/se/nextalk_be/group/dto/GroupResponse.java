package iuh.fit.se.nextalk_be.group.dto;

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
    private String ownerId;
    private String ownerUsername;
    private String conversationId;
    private List<GroupMemberResponse> members;
    private int memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
