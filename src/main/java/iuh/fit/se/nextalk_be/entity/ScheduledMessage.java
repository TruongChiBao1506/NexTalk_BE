package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.dto.request.MessageRequest;
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
@Document(collection = "scheduled_messages")
@CompoundIndex(name = "scheduled_status_time_idx", def = "{'status': 1, 'scheduledAt': 1}")
public class ScheduledMessage extends BaseEntity {
    @DocumentReference(lazy = true)
    private User sender;

    private MessageRequest payload;
    private LocalDateTime scheduledAt;

    @Builder.Default
    private ScheduledMessageStatus status = ScheduledMessageStatus.PENDING;

    @Builder.Default
    private int attempts = 0;

    private String sentMessageId;
    private String lastError;
    private LocalDateTime sentAt;
    private LocalDateTime cancelledAt;
}
