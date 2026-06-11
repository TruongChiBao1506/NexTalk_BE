package iuh.fit.se.nextalk_be.summary.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryResponse {
    @Builder.Default
    private String type = "CONVERSATION_SUMMARY";
    private String conversationId;
    private String summary;
    private int sourceMessageCount;
    private LocalDateTime createdAt;
}
