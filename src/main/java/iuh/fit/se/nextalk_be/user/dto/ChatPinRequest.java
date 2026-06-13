package iuh.fit.se.nextalk_be.user.dto;

import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatPinRequest {

    @Pattern(regexp = "\\d{4}", message = "PIN must be exactly 4 digits")
    private String pin;
}
