package iuh.fit.se.nextalk_be.entity;

import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tasks")
@org.springframework.data.mongodb.core.index.CompoundIndex(name = "task_conv_idx", def = "{'conversationId': 1}")
public class Task extends BaseEntity {

    private String conversationId;

    private String name;

    private String description;

    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Builder.Default
    private List<String> assigneeIds = new ArrayList<>();
    
    private String creatorId;

    private LocalDateTime startDate;

    private LocalDateTime dueDate;

    @Builder.Default
    private Integer progress = 0;
}
