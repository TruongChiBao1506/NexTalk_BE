package iuh.fit.se.nextalk_be.entity;

import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "group_events")
@CompoundIndex(name = "group_event_start_idx", def = "{'group': 1, 'status': 1, 'startsAt': 1}")
public class GroupEvent extends BaseEntity {
    @DocumentReference
    private Group group;

    @DocumentReference
    private Conversation conversation;

    @DocumentReference
    private User creator;

    private String title;
    private String description;
    private String location;
    private String meetingUrl;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private int reminderMinutes;

    @Indexed
    private LocalDateTime remindAt;

    @Builder.Default
    private GroupEventStatus status = GroupEventStatus.SCHEDULED;

    @Builder.Default
    private Map<String, GroupEventRsvpStatus> responses = new HashMap<>();

    private String messageId;

    @Builder.Default
    private boolean reminderSent = false;
}
