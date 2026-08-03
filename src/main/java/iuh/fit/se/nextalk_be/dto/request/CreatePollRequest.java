package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePollRequest {
    @NotBlank
    @Size(max = 100)
    private String conversationId;

    @NotBlank
    @Size(max = 500)
    private String question;

    @NotEmpty
    @Size(min = 2, max = 20)
    private List<@NotBlank @Size(max = 200) String> options;

    private boolean allowMultiple;
    private boolean allowAddOptions;
    private boolean anonymous;
    @Size(max = 80)
    private String expiresAt;
}
