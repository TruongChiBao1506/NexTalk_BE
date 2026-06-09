package iuh.fit.se.nextalk_be.friend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipAcceptRequest {

    @NotNull(message = "Sender ID is required")
    private String senderId;
}
