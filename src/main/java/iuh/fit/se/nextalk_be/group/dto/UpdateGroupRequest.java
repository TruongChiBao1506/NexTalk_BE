package iuh.fit.se.nextalk_be.group.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupRequest {

    @Size(min = 2, max = 50, message = "Group name must be between 2 and 50 characters")
    private String name;
    private String avatarUrl;

    private Boolean requiresApproval;
}
