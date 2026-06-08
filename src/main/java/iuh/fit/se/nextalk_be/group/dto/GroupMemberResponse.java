package iuh.fit.se.nextalk_be.group.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberResponse {
    private UUID userId;
    private String username;
    private String avatarUrl;
    private String role;
}
