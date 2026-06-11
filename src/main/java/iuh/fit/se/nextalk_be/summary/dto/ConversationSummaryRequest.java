package iuh.fit.se.nextalk_be.summary.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryRequest {
    private String conversationId;
    private String conversationName;
    private String conversationType;
    private String requestedByUserId;
    private String requestedByUsername;
    private int messageCount;
    private String preferredModel;
    private String instruction;
    private List<SummaryMessagePayload> messages;
}
