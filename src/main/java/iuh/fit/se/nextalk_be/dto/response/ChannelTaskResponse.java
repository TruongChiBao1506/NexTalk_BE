package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.entity.ChannelTaskPriority;
import iuh.fit.se.nextalk_be.entity.ChannelTaskStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelTaskResponse {
    private String id;
    private String groupId;
    private String groupName;
    private String channelId;
    private String channelName;
    private String conversationId;
    private String title;
    private String description;
    private ChannelTaskStatus status;
    private ChannelTaskPriority priority;
    private String createdById;
    private String createdByUsername;
    private List<ChannelTaskAssigneeResponse> assignees;
    private List<ChannelTaskAssigneeResponse> watchers;
    private List<TaskDependencyResponse> dependencies;
    private String startAt;
    private String dueAt;
    private String reminderAt;
    private String recurrence;
    private String recurrenceSourceTaskId;
    private String nextRecurringTaskId;
    private LocalDateTime completedAt;
    private List<SubtaskResponse> subtasks;
    private List<TaskAttachmentResponse> attachments;
    private TaskSourceMessageResponse sourceMessage;
    @com.fasterxml.jackson.annotation.JsonProperty("isPinned")
    private boolean isPinned;
    private LocalDateTime pinnedAt;
    @com.fasterxml.jackson.annotation.JsonProperty("isArchived")
    private boolean isArchived;
    private LocalDateTime archivedAt;
    private String archivedById;
    private String archivedByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
