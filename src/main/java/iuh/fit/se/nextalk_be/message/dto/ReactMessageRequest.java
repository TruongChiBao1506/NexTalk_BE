package iuh.fit.se.nextalk_be.message.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactMessageRequest {
    @NotBlank(message = "Emoji is required")
    private String emoji;
}
