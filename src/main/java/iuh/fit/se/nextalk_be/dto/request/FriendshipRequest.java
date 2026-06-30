package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipRequest {

    @NotNull(message = "Receiver ID is required")
    private String receiverId;
}
