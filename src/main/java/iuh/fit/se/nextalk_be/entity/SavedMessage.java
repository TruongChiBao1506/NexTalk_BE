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
@Document(collection = "saved_messages")
@CompoundIndex(name = "saved_message_user_message_idx", def = "{'user': 1, 'message': 1}", unique = true)
@CompoundIndex(name = "saved_message_user_created_idx", def = "{'user': 1, 'createdAt': -1}")
public class SavedMessage extends BaseEntity {
    @DocumentReference
    private User user;

    @DocumentReference
    private Message message;
}
