package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelTaskAssigneeResponse {
    private String userId;
    private String username;
    private String avatarUrl;
}
