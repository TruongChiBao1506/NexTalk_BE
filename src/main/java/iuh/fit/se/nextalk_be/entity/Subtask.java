package iuh.fit.se.nextalk_be.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subtask {
    private String id;
    private String title;

    @JsonProperty("isCompleted")
    private boolean isCompleted;
}
