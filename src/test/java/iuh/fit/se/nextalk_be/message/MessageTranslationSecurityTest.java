package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.dto.request.TranslateMessageRequest;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageTranslationResponse;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.MessageService;
import iuh.fit.se.nextalk_be.service.MessageTranslationService;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageTranslationSecurityTest {
    @Mock MessageService messageService;
    @Mock UserService userService;
    @Mock RateLimitService rateLimitService;
    @Mock RestTemplate restTemplate;
    @InjectMocks MessageTranslationService service;

    @Test
    void translationRejectsMessageOutsideCurrentUsersConversationsBeforeCallingAi() {
        when(messageService.getMessageForCurrentUser("message-b"))
                .thenThrow(new BadRequestException("not a member"));

        assertThrows(BadRequestException.class, () -> service.translate(
                "message-b", new TranslateMessageRequest("vi")));
        verifyNoInteractions(restTemplate, rateLimitService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void translationUsesCloudTranslationWithoutExposingCredentialInUrlOrPayload() {
        MessageResponse message = MessageResponse.builder()
                .id("message-a")
                .content("<p>Xin chào</p>")
                .messageType("TEXT")
                .build();
        User user = User.builder().username("member").build();
        user.setId("user-a");

        ReflectionTestUtils.setField(service, "googleApiKey", "placeholder-credential");
        ReflectionTestUtils.setField(service, "googleTranslateUrl",
                "https://translation.googleapis.com/language/translate/v2");
        when(messageService.getMessageForCurrentUser("message-a")).thenReturn(message);
        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "data", Map.of(
                                "translations", List.of(Map.of("translatedText", "Hello &amp; welcome"))))));

        MessageTranslationResponse result = service.translate(
                "message-a", new TranslateMessageRequest("en"));

        assertEquals("Hello & welcome", result.getTranslatedText());
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://translation.googleapis.com/language/translate/v2"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Object.class));
        HttpEntity<Map<String, Object>> entity = entityCaptor.getValue();
        assertEquals("placeholder-credential", entity.getHeaders().getFirst("x-goog-api-key"));
        assertEquals("Xin chào", entity.getBody().get("q"));
        assertEquals("en", entity.getBody().get("target"));
        assertEquals("text", entity.getBody().get("format"));
        assertEquals("nmt", entity.getBody().get("model"));
        assertFalse(entity.getBody().containsValue("placeholder-credential"));
    }
}
