package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.GroupEventRsvpStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupEventRsvpRequest {
    @NotNull
    private GroupEventRsvpStatus status;
}
