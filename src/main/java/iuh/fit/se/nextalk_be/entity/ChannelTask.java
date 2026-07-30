package iuh.fit.se.nextalk_be.entity;

import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "channel_tasks")
@CompoundIndex(name = "group_channel_archived_status_due_idx", def = "{'group': 1, 'channel': 1, 'archived': 1, 'status': 1, 'dueAt': 1}")
@CompoundIndex(name = "task_reminder_scan_idx", def = "{'archived': 1, 'status': 1, 'reminderSent': 1, 'reminderAt': 1}")
@CompoundIndex(name = "task_overdue_scan_idx", def = "{'archived': 1, 'status': 1, 'overdueNotificationSent': 1, 'dueAt': 1}")
@CompoundIndex(name = "task_approaching_scan_idx", def = "{'archived': 1, 'status': 1, 'approachingNotificationSent': 1, 'dueAt': 1}")
public class ChannelTask extends BaseEntity {

    private String title;

    private String description;

    @Builder.Default
    private ChannelTaskStatus status = ChannelTaskStatus.TODO;

    @Builder.Default
    private ChannelTaskPriority priority = ChannelTaskPriority.MEDIUM;

    private LocalDateTime startAt;

    private LocalDateTime dueAt;

    private LocalDateTime completedAt;

    @DocumentReference(lazy = true)
    private Group group;

    @DocumentReference(lazy = true)
    private Channel channel;

    @DocumentReference(lazy = true)
    private User createdBy;

    @Builder.Default
    @DocumentReference(lazy = true)
    private Set<User> assignees = new HashSet<>();

    @Builder.Default
    @DocumentReference(lazy = true)
    private Set<User> watchers = new HashSet<>();

    @Builder.Default
    private Set<String> dependencyTaskIds = new HashSet<>();

    private LocalDateTime reminderAt;

    @Builder.Default
    private boolean reminderSent = false;

    @Builder.Default
    private boolean overdueNotificationSent = false;

    @Builder.Default
    private boolean approachingNotificationSent = false;

    @Builder.Default
    private TaskRecurrence recurrence = TaskRecurrence.NONE;

    @Builder.Default
    private boolean recurrenceSpawned = false;

    private String recurrenceSourceTaskId;

    private String nextRecurringTaskId;

    @Builder.Default
    private boolean isPinned = false;

    private LocalDateTime pinnedAt;

    @Builder.Default
    private boolean archived = false;

    private LocalDateTime archivedAt;

    @DocumentReference(lazy = true)
    private User archivedBy;

    @Builder.Default
    private java.util.List<Subtask> subtasks = new java.util.ArrayList<>();

    @Builder.Default
    private java.util.List<TaskAttachment> attachments = new java.util.ArrayList<>();

    private TaskSourceMessage sourceMessage;
}
