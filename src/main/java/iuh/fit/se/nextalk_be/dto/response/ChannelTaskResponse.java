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
    private String channelId;
    private String title;
    private String description;
    private ChannelTaskStatus status;
    private ChannelTaskPriority priority;
    private String createdById;
    private String createdByUsername;
    private List<ChannelTaskAssigneeResponse> assignees;
    private String dueAt;
    private LocalDateTime completedAt;
    private List<SubtaskResponse> subtasks;
    private List<TaskAttachmentResponse> attachments;
    @com.fasterxml.jackson.annotation.JsonProperty("isPinned")
    private boolean isPinned;
    private LocalDateTime pinnedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
