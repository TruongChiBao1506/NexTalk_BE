package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.ChannelTaskPriority;
import iuh.fit.se.nextalk_be.entity.ChannelTaskStatus;
import iuh.fit.se.nextalk_be.entity.TaskRecurrence;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateChannelTaskRequest {
    private String title;
    private String description;
    private ChannelTaskStatus status;
    private ChannelTaskPriority priority;
    private String startAt;
    private String dueAt;
    private Set<String> assigneeIds;
    private Set<String> watcherIds;
    private Set<String> dependencyTaskIds;
    private String reminderAt;
    private TaskRecurrence recurrence;
    private java.util.List<SubtaskRequest> subtasks;
    private java.util.List<TaskAttachmentRequest> attachments;
}
