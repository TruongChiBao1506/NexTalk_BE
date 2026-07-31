package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TranslateMessageRequest {
    @NotBlank
    @Pattern(regexp = "vi|en|ja|ko|zh-CN|fr|de|es|th|id")
    private String targetLanguage;
}
