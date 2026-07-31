package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTagDataResponse {
    private List<ConversationTagResponse> tags;
    // Map of targetId (Conversation ID) -> List of Tag IDs assigned to it
    private Map<String, List<String>> mappings;
}
