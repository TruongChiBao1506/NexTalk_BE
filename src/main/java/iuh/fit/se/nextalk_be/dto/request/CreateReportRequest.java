package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {
    @NotBlank(message = "Reported user ID cannot be empty")
    private String reportedUserId;

    private String conversationId;

    @NotBlank(message = "Reason cannot be empty")
    private String reason;

    private String description;
}
