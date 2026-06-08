package iuh.fit.se.nextalk_be.message;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReaction {
    private UUID userId;
    private String username;
    private String emoji;
}
