package iuh.fit.se.nextalk_be.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubtaskResponse {
    private String id;
    private String title;

    @JsonProperty("isCompleted")
    private boolean isCompleted;
}
