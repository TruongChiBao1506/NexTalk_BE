package iuh.fit.se.nextalk_be.group.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberResponse {
    private String userId;
    private String username;
    private String avatarUrl;
    private String role;
}
