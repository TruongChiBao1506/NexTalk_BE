package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.ChannelTaskPriority;
import iuh.fit.se.nextalk_be.entity.ChannelTaskStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChannelTaskRequest {

    @NotBlank(message = "Task title is required")
    private String title;

    private String description;

    private ChannelTaskStatus status;

    private ChannelTaskPriority priority;

    private String dueAt;

    private Set<String> assigneeIds;

    private java.util.List<SubtaskRequest> subtasks;
}
