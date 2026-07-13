package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.User;


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
@CompoundIndex(name = "status_conv_user_state_idx", def = "{'conversationId': 1, 'userId': 1, 'status': 1}")
@CompoundIndex(name = "status_message_user_v2", def = "{'messageId': 1, 'userId': 1}", unique = true, sparse = true)
public class MessageStatus extends BaseEntity {

    @DocumentReference(lazy = true)
    private Message message;

    @DocumentReference(lazy = true)
    private User user;

    private String conversationId;
    private String messageId;
    private String userId;

    private String status; // SENT, DELIVERED, SEEN
}
