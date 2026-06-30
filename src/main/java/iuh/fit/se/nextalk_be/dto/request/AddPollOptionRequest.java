package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddPollOptionRequest {
    @NotBlank
    private String text;
}
