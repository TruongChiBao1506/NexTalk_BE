package iuh.fit.se.nextalk_be.group.dto;

import iuh.fit.se.nextalk_be.group.GroupRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMemberRoleRequest {

    @NotNull(message = "Role is required")
    private GroupRole role;
}
