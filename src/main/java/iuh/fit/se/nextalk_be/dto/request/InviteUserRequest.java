package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.User;


import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteUserRequest {
    @NotBlank(message = "User ID is required")
    private String userId;
}
