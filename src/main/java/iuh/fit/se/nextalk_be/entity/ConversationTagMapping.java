package iuh.fit.se.nextalk_be.entity;

import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversation_tag_mappings")
@CompoundIndex(name = "conv_tag_map_user_target_tag_idx", def = "{'user': 1, 'targetId': 1, 'tag': 1}", unique = true)
@CompoundIndex(name = "conv_tag_map_user_tag_idx", def = "{'user': 1, 'tag': 1}")
public class ConversationTagMapping extends BaseEntity {
    @DocumentReference
    private User user;

    @DocumentReference
    private ConversationTag tag;

    private String targetType; // "DM" or "GROUP"
    private String targetId;   // Conversation ID (DM or Group ID)
}
