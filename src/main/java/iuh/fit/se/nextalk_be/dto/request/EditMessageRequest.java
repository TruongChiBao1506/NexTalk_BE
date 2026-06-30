package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.Message;


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
