package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.ModerationDecision;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModerationDecisionRequest {
    @NotNull(message = "Moderation decision is required")
    private ModerationDecision decision;
}
