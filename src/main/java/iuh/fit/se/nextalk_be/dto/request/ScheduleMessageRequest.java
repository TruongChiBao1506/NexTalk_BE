package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleMessageRequest {
    @Valid
    @NotNull(message = "Message is required")
    private MessageRequest message;

    @NotBlank(message = "Scheduled time is required")
    private String scheduledAt;

    private boolean silent;
}
