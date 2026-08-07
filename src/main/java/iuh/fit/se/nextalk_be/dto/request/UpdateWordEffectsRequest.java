package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.Conversation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWordEffectsRequest {
    @NotNull
    @Valid
    private List<Conversation.WordEffectItem> wordEffects;
}
