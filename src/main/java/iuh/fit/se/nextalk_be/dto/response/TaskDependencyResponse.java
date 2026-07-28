package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.entity.ChannelTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDependencyResponse {
    private String taskId;
    private String title;
    private ChannelTaskStatus status;
}
