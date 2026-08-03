package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactMessageRequest {
    @NotBlank(message = "Emoji is required")
    @Size(max = 32, message = "Reaction must not exceed 32 characters")
    private String emoji;
}
