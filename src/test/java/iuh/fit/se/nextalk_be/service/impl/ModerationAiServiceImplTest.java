package iuh.fit.se.nextalk_be.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.entity.UserReport;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.UserReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationAiServiceImplTest {
    @Mock private UserReportRepository userReportRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private RestTemplate restTemplate;

    @Test
    void aiBanOutputIsDowngradedToHumanReviewAndCannotLockUser() {
        ModerationAiServiceImpl service = new ModerationAiServiceImpl(
                userReportRepository,
                messageRepository,
                conversationRepository,
                restTemplate);
        ReflectionTestUtils.setField(service, "geminiApiKey", "test-key");
        ReflectionTestUtils.setField(service, "geminiModel", "test-model");
        ReflectionTestUtils.setField(service, "geminiUrl", "https://example.test/{model}?key={key}");

        User reporter = user("reporter-1", "reporter");
        User reported = user("reported-1", "reported");
        Conversation conversation = Conversation.builder().members(Set.of(reporter, reported)).build();
        conversation.setId("conversation-1");
        UserReport report = UserReport.builder()
                .reporter(reporter)
                .reportedUser(reported)
                .conversationId(conversation.getId())
                .reason("Abuse")
                .status("PENDING")
                .build();
        report.setId("report-1");
        Message message = Message.builder().sender(reported).content("Untrusted evidence").build();
        message.setId("message-1");

        when(userReportRepository.findById(report.getId())).thenReturn(Optional.of(report), Optional.of(report));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(messageRepository.findVisibleConversationMessages(
                eq(conversation.getId()), eq(reporter.getId()), any()))
                .thenReturn(new SliceImpl<>(List.of(message)));
        Map<String, Object> aiBody = Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of(
                                        "text", "{\"suggestedAction\":\"BAN\",\"reasoning\":\"model output\"}"))))));
        when(restTemplate.postForEntity(any(String.class), any(), eq(Object.class), any(), any()))
                .thenReturn(ResponseEntity.ok(aiBody));

        service.evaluateReportAsync(report.getId());

        ArgumentCaptor<UserReport> saved = ArgumentCaptor.forClass(UserReport.class);
        verify(userReportRepository).save(saved.capture());
        assertEquals("HUMAN_REVIEW", saved.getValue().getAiVerdict());
        assertEquals("PENDING_REVIEW", saved.getValue().getStatus());
        assertEquals(false, reported.isAccountLocked());
    }

    private User user(String id, String username) {
        User user = User.builder().email(username + "@example.test").username(username).build();
        user.setId(id);
        return user;
    }
}
