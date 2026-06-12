package iuh.fit.se.nextalk_be.message.dto;

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
