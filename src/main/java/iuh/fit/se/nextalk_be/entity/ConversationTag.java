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
@Document(collection = "conversation_tags")
@CompoundIndex(name = "conv_tag_user_name_idx", def = "{'user': 1, 'name': 1}", unique = true)
@CompoundIndex(name = "conv_tag_user_pos_idx", def = "{'user': 1, 'position': 1}")
public class ConversationTag extends BaseEntity {
    @DocumentReference
    private User user;

    private String name;
    private String color;
    private Integer position;
}
