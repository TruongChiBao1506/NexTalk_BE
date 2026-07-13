package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchMessageIdsRequest {
    @NotEmpty(message = "Message IDs list cannot be empty")
    private List<String> messageIds;
}
