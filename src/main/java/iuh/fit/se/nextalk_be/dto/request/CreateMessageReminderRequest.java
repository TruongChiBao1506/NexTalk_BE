package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMessageReminderRequest {

    @NotBlank(message = "Message ID is required")
    private String messageId;

    @NotBlank(message = "Reminder time is required")
    private String remindAt;

    private String note;
}
