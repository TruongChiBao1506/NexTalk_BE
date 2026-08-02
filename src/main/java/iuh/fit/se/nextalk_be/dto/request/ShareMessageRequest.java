package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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

    @Size(max = 1000, message = "Accompanying text must not exceed 1000 characters")
    private String accompanyingText;
}
