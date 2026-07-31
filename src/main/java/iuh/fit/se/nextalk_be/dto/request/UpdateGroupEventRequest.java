package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupEventRequest {
    @NotBlank
    @Size(max = 120)
    private String title;

    @Size(max = 1000)
    private String description;

    @Size(max = 300)
    private String location;

    @Size(max = 500)
    private String meetingUrl;

    @NotNull
    @Future
    private LocalDateTime startsAt;

    private LocalDateTime endsAt;
    private int reminderMinutes = 60;
}
