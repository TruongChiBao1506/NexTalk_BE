package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationTagRequest {
    @NotBlank(message = "Tag name cannot be blank")
    @Size(max = 50, message = "Tag name cannot exceed 50 characters")
    private String name;

    @NotBlank(message = "Color cannot be blank")
    private String color;
}
