package iuh.fit.se.nextalk_be.dto;

import lombok.*;

import java.time.LocalDateTime;

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
    private String signalType; // INVITE, ANSWER, CALL_HANDLED, HANDOFF_REQUEST, HANDOFF_ACCEPTED, CANCEL, HANGUP, LEAVE, BUSY
    private String token;
    private Integer uid;
    private Boolean accept;
    private String reason; // busy, rejected
    private String handledByDeviceId;
    private String sourceDeviceId;
    private String handoffPeerId;
    private String handoffPeerName;
    private String handoffPeerAvatar;
    private String callState; // RINGING_INCOMING, RINGING_OUTGOING, CONNECTED
    private LocalDateTime startedAt;
    private Long connectedAtEpochMs;
    private Long expiresAt;
}
