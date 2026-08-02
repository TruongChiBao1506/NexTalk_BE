package iuh.fit.se.nextalk_be.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversation_unread_markers")
@CompoundIndex(name = "unread_marker_user_conversation", def = "{'userId': 1, 'conversationId': 1}", unique = true)
public class ConversationUnreadMarker extends BaseEntity {
    private String userId;
    private String conversationId;
    private String messageId;
}
