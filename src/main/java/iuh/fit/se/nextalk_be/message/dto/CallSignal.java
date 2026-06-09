package iuh.fit.se.nextalk_be.message.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSignal {
    private String conversationId;
    private String callerId;
    private String receiverId;
    private String callerName;
    private String callerAvatar;
    private String type; // VOICE, VIDEO
    private String signalType; // INVITE, ANSWER, CANCEL, HANGUP, BUSY
    private String token;
    private Integer uid;
    private Boolean accept;
}
