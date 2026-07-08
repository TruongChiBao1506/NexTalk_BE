package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.dto.SummaryMessagePayload;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBotRequest {
    private String conversationId;
    private String conversationName;
    private String conversationType;
    private String requestedByUserId;
    private String requestedByUsername;
    private String question;
    private String triggerMessageId;
    private int messageCount;
    private String preferredModel;
    private String instruction;
    private List<SummaryMessagePayload> messages;
}
