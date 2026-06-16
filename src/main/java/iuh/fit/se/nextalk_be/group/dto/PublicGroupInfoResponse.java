package iuh.fit.se.nextalk_be.group.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicGroupInfoResponse {
    private String id;
    private String name;
    private String avatarUrl;
    private String ownerUsername;
    private int memberCount;
    private boolean requiresApproval;
}
