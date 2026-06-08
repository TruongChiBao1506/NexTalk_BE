package iuh.fit.se.nextalk_be.notification;

import iuh.fit.se.nextalk_be.common.BaseEntity;
import iuh.fit.se.nextalk_be.user.User;
import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
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
