package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationWithPreviewsResponse {
    private List<ConversationResponse> conversations;
    private Map<String, MessageResponse> lastMessages;
    private Map<String, Long> unreadCounts;
}
