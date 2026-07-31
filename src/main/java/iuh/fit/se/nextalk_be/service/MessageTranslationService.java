package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.TranslateMessageRequest;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageTranslationResponse;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageTranslationService {
    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "vi", "Vietnamese", "en", "English", "ja", "Japanese", "ko", "Korean",
            "zh-CN", "Simplified Chinese", "fr", "French", "de", "German",
            "es", "Spanish", "th", "Thai", "id", "Indonesian");

    private final MessageService messageService;
    private final UserService userService;
    private final RateLimitService rateLimitService;
    private final RestTemplate restTemplate;

    @Value("${app.translation.google-api-key:${GOOGLE_TRANSLATE_API_KEY:}}")
    private String googleApiKey;

    @Value("${app.translation.google-url:https://translation.googleapis.com/language/translate/v2}")
    private String googleTranslateUrl;

    public MessageTranslationResponse translate(String messageId, TranslateMessageRequest request) {
        MessageResponse message = messageService.getMessageForCurrentUser(messageId);
        if (message.isRecalled() || message.getContent() == null || message.getContent().isBlank()
                || "SYSTEM".equals(message.getMessageType()) || "STICKER".equals(message.getMessageType())) {
            throw new BadRequestException("This message does not contain translatable text");
        }
        String source = Jsoup.parse(message.getContent()).text().trim();
        if (source.length() > 5_000) {
            throw new BadRequestException("Message is too long to translate");
        }
        if (!LANGUAGE_NAMES.containsKey(request.getTargetLanguage())) {
            throw new BadRequestException("Unsupported target language");
        }
        if (googleApiKey == null || googleApiKey.isBlank()) {
            throw new BadRequestException("Translation service is not configured");
        }

        String userId = userService.getCurrentAuthenticatedUser().getId();
        rateLimitService.check("ai:message-translation", userId, 30, Duration.ofMinutes(5));

        Map<String, Object> payload = Map.of(
                "q", source,
                "target", request.getTargetLanguage(),
                "format", "text",
                "model", "nmt");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", googleApiKey);
        try {
            ResponseEntity<Object> response = restTemplate.exchange(
                    googleTranslateUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Object.class);
            String translated = response.getStatusCode().is2xxSuccessful()
                    ? extractGoogleTranslation(response.getBody()) : null;
            if (translated == null || translated.isBlank()) throw new BadRequestException("Translation is unavailable");
            return MessageTranslationResponse.builder()
                    .messageId(messageId)
                    .targetLanguage(request.getTargetLanguage())
                    .translatedText(HtmlUtils.htmlUnescape(translated.trim()))
                    .build();
        } catch (BadRequestException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new BadRequestException("Translation service credentials are invalid");
            }
            if (exception.getStatusCode().value() == 429) {
                throw new BadRequestException("Translation service is busy. Please try again shortly");
            }
            throw new BadRequestException("Translation is unavailable");
        } catch (Exception ignored) {
            throw new BadRequestException("Translation is unavailable");
        }
    }

    private String extractGoogleTranslation(Object body) {
        if (!(body instanceof Map<?, ?> map)) return null;
        Object dataRaw = map.get("data");
        if (!(dataRaw instanceof Map<?, ?> data)) return null;
        Object translationsRaw = data.get("translations");
        if (!(translationsRaw instanceof java.util.List<?> translations) || translations.isEmpty()) return null;
        Object firstRaw = translations.get(0);
        if (!(firstRaw instanceof Map<?, ?> first)) return null;
        Object translatedText = first.get("translatedText");
        return translatedText == null ? null : String.valueOf(translatedText);
    }
}
