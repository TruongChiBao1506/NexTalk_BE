package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.common.BaseEntity;
import iuh.fit.se.nextalk_be.user.User;
import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "message_statuses")
@CompoundIndex(name = "message_user_idx", def = "{'message': 1, 'user': 1}", unique = true)
public class MessageStatus extends BaseEntity {

    @DocumentReference
    private Message message;

    @DocumentReference
    private User user;

    private String status; // SENT, DELIVERED, SEEN
}
