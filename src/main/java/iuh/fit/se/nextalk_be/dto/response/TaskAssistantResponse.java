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
public class TaskAssistantResponse {
    private String status;
    private String reply;
    private String confirmationId;
    private TaskAssistantActionResponse action;
    private Map<String, Object> result;
}
