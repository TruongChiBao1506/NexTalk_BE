package iuh.fit.se.nextalk_be.message.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareMessageRequest {

    @NotEmpty(message = "Target conversation IDs are required")
    private List<String> targetConversationIds;
}
