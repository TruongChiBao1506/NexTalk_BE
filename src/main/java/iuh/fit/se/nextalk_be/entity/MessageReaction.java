package iuh.fit.se.nextalk_be.entity;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReaction {
    private String userId;
    private String username;
    private String emoji;
}
