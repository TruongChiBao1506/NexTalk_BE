package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.GroupRole;


import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMemberRoleRequest {

    @NotNull(message = "Role is required")
    private GroupRole role;
}
