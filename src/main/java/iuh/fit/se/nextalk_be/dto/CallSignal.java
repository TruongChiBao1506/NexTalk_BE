package iuh.fit.se.nextalk_be.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSignal {
    private String callId;
    private String conversationId;
    private String groupName;
    private Integer groupMemberCount;
    private String callerId;
    private String receiverId;
    private String callerName;
    private String callerAvatar;
    private String type; // VOICE, VIDEO
    private String signalType; // INVITE, ANSWER, CANCEL, HANGUP, LEAVE, BUSY
    private String token;
    private Integer uid;
    private Boolean accept;
    private String reason; // busy, rejected
}
