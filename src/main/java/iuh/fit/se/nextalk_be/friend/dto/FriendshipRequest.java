package iuh.fit.se.nextalk_be.friend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipRequest {

    @NotNull(message = "Receiver ID is required")
    private UUID receiverId;
}
