package iuh.fit.se.nextalk_be.dto.response;

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
