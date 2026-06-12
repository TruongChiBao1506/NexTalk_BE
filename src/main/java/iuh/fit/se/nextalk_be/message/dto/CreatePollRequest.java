package iuh.fit.se.nextalk_be.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePollRequest {
    @NotBlank
    private String conversationId;

    @NotBlank
    private String question;

    @NotEmpty
    private List<String> options;

    private boolean allowMultiple;
    private boolean allowAddOptions;
    private boolean anonymous;
    private LocalDateTime expiresAt;
}
