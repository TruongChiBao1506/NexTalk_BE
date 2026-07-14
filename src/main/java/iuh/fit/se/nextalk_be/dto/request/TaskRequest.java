package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.TaskPriority;
import iuh.fit.se.nextalk_be.entity.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskRequest {
    private String conversationId;
    private String name;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private List<String> assigneeIds;
    private LocalDateTime startDate;
    private LocalDateTime dueDate;
    private Integer progress;
}
