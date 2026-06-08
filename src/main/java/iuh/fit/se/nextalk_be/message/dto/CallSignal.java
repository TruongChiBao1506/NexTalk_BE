package iuh.fit.se.nextalk_be.message.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSignal {
    private UUID conversationId;
    private UUID callerId;
    private UUID receiverId;
    private String callerName;
    private String callerAvatar;
    private String type; // VOICE, VIDEO
    private String signalType; // INVITE, ANSWER, CANCEL, HANGUP, BUSY
    private String token;
    private Integer uid;
    private Boolean accept;
}
