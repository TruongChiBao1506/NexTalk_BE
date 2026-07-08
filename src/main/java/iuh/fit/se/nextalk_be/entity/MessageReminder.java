package iuh.fit.se.nextalk_be.entity;

import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "message_reminders")
@CompoundIndex(name = "reminder_user_status_time_idx", def = "{'user': 1, 'status': 1, 'remindAt': 1}")
public class MessageReminder extends BaseEntity {

    @DocumentReference(lazy = true)
    private User user;

    @DocumentReference(lazy = true)
    private Conversation conversation;

    @DocumentReference(lazy = true)
    private Message message;

    private LocalDateTime remindAt;

    private String note;

    private String messagePreview;

    private String senderUsername;

    @Builder.Default
    private MessageReminderStatus status = MessageReminderStatus.PENDING;

    private LocalDateTime firedAt;

    private LocalDateTime deletedAt;
}
