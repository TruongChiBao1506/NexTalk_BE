package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;
import iuh.fit.se.nextalk_be.entity.NotificationType;
import iuh.fit.se.nextalk_be.entity.User;


import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
@CompoundIndex(name = "notif_recipient_created_idx", def = "{'recipient._id': 1, 'createdAt': -1}")
@CompoundIndex(name = "notif_recipient_read_idx", def = "{'recipient._id': 1, 'isRead': 1}")
public class Notification extends BaseEntity {

    @DocumentReference
    private User recipient;

    @Indexed
    private NotificationType type;

    private String content;

    // Optional reference to related entity (friendshipId, groupId, conversationId)
    private String referenceId;

    @Builder.Default
    private boolean isRead = false;
}
