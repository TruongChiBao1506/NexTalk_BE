package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.entity.TaskPriority;
import iuh.fit.se.nextalk_be.entity.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TaskResponse {
    private String id;
    private String conversationId;
    private String name;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private List<String> assigneeIds;
    private String creatorId;
    private LocalDateTime startDate;
    private LocalDateTime dueDate;
    private Integer progress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
