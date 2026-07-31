package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignConversationTagRequest {
    @NotBlank(message = "Target ID cannot be blank")
    private String targetId;

    private String targetType; // "DM" or "GROUP"
}
