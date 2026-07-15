package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.entity.TaskActivityType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TaskActivityResponse {
    private String id;
    private String groupId;
    private String channelId;
    private String taskId;
    private String actorId;
    private String actorUsername;
    private String actorAvatarUrl;
    private TaskActivityType type;
    private String content;

    @JsonProperty("isRead")
    private boolean isRead;

    private LocalDateTime createdAt;
}
