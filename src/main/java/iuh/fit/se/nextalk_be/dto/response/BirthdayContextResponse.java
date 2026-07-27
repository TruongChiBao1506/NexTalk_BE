package iuh.fit.se.nextalk_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BirthdayContextResponse {
    private boolean hasBirthday;
    private String userId;
    private String displayName;
    private int daysUntil;
    private String message;
    private List<String> templates;
}
