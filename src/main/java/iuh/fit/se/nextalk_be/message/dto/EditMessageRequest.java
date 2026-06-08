package iuh.fit.se.nextalk_be.message.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditMessageRequest {
    @NotBlank(message = "Message content cannot be blank")
    private String content;
}
