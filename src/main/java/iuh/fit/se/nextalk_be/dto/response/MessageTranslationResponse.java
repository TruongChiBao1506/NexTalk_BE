package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageTranslationResponse {
    private String messageId;
    private String targetLanguage;
    private String translatedText;
}
