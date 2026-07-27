package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskAssistantRequest {
    @NotBlank(message = "Conversation ID is required")
    private String conversationId;

    @NotBlank(message = "Assistant request is required")
    @Size(min = 3, max = 2000, message = "Assistant request must be between 3 and 2000 characters")
    private String prompt;

    private String groupId;
    private String channelId;
}
