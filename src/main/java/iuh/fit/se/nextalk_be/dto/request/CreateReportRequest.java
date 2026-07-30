package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {
    @NotBlank(message = "Reported user ID cannot be empty")
    private String reportedUserId;

    @NotBlank(message = "Conversation ID cannot be empty")
    private String conversationId;

    @NotBlank(message = "Reason cannot be empty")
    @Size(max = 100, message = "Reason must not exceed 100 characters")
    private String reason;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;
}
