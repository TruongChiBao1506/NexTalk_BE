package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSelfDestructRequest {
    @NotNull(message = "Self destruct duration is required")
    private Integer selfDestructSeconds;
}
