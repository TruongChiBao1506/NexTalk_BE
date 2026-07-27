package iuh.fit.se.nextalk_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAssistantActionResponse {
    private String toolName;
    private String label;
    private String summary;
    private Map<String, Object> arguments;
}
