package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.User;


import jakarta.validation.constraints.NotNull;
import lombok.*;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {

    @NotNull(message = "User ID is required")
    private String userId;
}
