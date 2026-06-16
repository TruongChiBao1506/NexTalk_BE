package iuh.fit.se.nextalk_be.group.dto;

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
