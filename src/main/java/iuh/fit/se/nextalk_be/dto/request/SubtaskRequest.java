package iuh.fit.se.nextalk_be.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SubtaskRequest {
    private String id;
    private String title;

    @JsonProperty("isCompleted")
    private Boolean isCompleted;
}
