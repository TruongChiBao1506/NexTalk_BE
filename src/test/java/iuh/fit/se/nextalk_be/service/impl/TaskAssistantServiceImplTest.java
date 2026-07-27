package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.TaskAssistantRequest;
import iuh.fit.se.nextalk_be.dto.response.TaskAssistantResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageReminderResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.TaskAssistantPendingAction;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.TaskAssistantPendingActionRepository;
import iuh.fit.se.nextalk_be.service.ChannelTaskService;
import iuh.fit.se.nextalk_be.service.MessageReminderService;
import iuh.fit.se.nextalk_be.service.MessageService;
import iuh.fit.se.nextalk_be.service.ScheduledMessageService;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAssistantServiceImplTest {
    private static final String INTERACTIONS_URL = "https://example.test/interactions";

    @Mock private ConversationRepository conversationRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private TaskAssistantPendingActionRepository pendingActionRepository;
    @Mock private MessageService messageService;
    @Mock private MessageReminderService messageReminderService;
    @Mock private ScheduledMessageService scheduledMessageService;
    @Mock private ChannelTaskService channelTaskService;
    @Mock private UserService userService;
    @Mock private RestTemplate restTemplate;

    private TaskAssistantServiceImpl service;
    private User user;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        service = new TaskAssistantServiceImpl(
                conversationRepository,
                channelRepository,
                messageRepository,
                pendingActionRepository,
                messageService,
                messageReminderService,
                scheduledMessageService,
                channelTaskService,
                userService,
                restTemplate
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "agent", "antigravity-preview-05-2026");
        ReflectionTestUtils.setField(service, "model", "gemini-3.6-flash");
        ReflectionTestUtils.setField(service, "interactionsUrl", INTERACTIONS_URL);
        ReflectionTestUtils.setField(service, "maxTotalTokens", 20_000);
        ReflectionTestUtils.setField(service, "confirmationMinutes", 15);

        user = User.builder().username("tester").email("tester@example.com").build();
        user.setId("user-1");
        conversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(Set.of(user))
                .build();
        conversation.setId("conversation-1");

        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
    }

    @Test
    void ask_ReturnsCompletedTextWithoutExecutingNexTalkMutation() {
        when(restTemplate.postForEntity(eq(INTERACTIONS_URL), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "id", "interaction-1",
                        "status", "completed",
                        "output_text", "Đã tìm thấy nội dung bạn cần."
                )));

        TaskAssistantResponse result = service.ask(request("Tìm nội dung báo cáo"));

        assertEquals("COMPLETED", result.getStatus());
        assertEquals("Đã tìm thấy nội dung bạn cần.", result.getReply());
        verifyNoInteractions(messageReminderService, channelTaskService, pendingActionRepository);
    }

    @Test
    void ask_MutationRequiresConfirmationAndDoesNotCreateReminderImmediately() {
        Message message = Message.builder().conversationId(conversation.getId()).build();
        message.setId("message-1");
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(restTemplate.postForEntity(eq(INTERACTIONS_URL), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "id", "interaction-2",
                        "environment_id", "environment-1",
                        "status", "requires_action",
                        "steps", List.of(Map.of(
                                "type", "function_call",
                                "id", "call-1",
                                "name", "schedule_reminder",
                                "arguments", Map.of(
                                        "messageId", message.getId(),
                                        "remindAt", "2026-07-28T09:00:00+07:00",
                                        "note", "Nộp báo cáo"
                                )
                        ))
                )));
        when(pendingActionRepository.save(any(TaskAssistantPendingAction.class)))
                .thenAnswer(invocation -> {
                    TaskAssistantPendingAction pending = invocation.getArgument(0);
                    pending.setId("confirmation-1");
                    return pending;
                });

        TaskAssistantResponse result = service.ask(request("Nhắc tôi nộp báo cáo vào sáng mai"));

        assertEquals("CONFIRMATION_REQUIRED", result.getStatus());
        assertEquals("confirmation-1", result.getConfirmationId());
        assertNotNull(result.getAction());
        assertEquals("schedule_reminder", result.getAction().getToolName());
        verifyNoInteractions(messageReminderService, channelTaskService);
    }

    @Test
    void ask_SimpleLatestMessageReminderUsesFastPathWithoutAntigravity() {
        Message latestMessage = Message.builder()
                .conversationId(conversation.getId())
                .sender(user)
                .content("Nội dung mới nhất")
                .build();
        latestMessage.setId("latest-message");
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(
                eq(conversation.getId()),
                any()
        )).thenReturn(new PageImpl<>(List.of(latestMessage)));
        when(pendingActionRepository.save(any(TaskAssistantPendingAction.class)))
                .thenAnswer(invocation -> {
                    TaskAssistantPendingAction pending = invocation.getArgument(0);
                    pending.setId("fast-confirmation");
                    return pending;
                });

        TaskAssistantResponse result = service.ask(
                request("Nhắc tôi kiểm tra lại tin nhắn mới nhất 15 phút nữa")
        );

        assertEquals("CONFIRMATION_REQUIRED", result.getStatus());
        assertEquals("fast-confirmation", result.getConfirmationId());
        assertEquals("schedule_reminder", result.getAction().getToolName());
        assertEquals("Kiểm tra lại tin nhắn mới nhất", result.getAction().getArguments().get("note"));
        verifyNoInteractions(restTemplate, messageReminderService, channelTaskService);
    }

    @Test
    void confirm_CreatesReminderWithoutCallingAntigravityAgain() {
        Message message = Message.builder().conversationId(conversation.getId()).build();
        message.setId("message-1");
        TaskAssistantPendingAction pending = TaskAssistantPendingAction.builder()
                .userId(user.getId())
                .conversationId(conversation.getId())
                .interactionId("interaction-1")
                .environmentId("environment-1")
                .callId("call-1")
                .toolName("schedule_reminder")
                .arguments(Map.of(
                        "messageId", message.getId(),
                        "remindAt", "2026-07-28T09:00:00+07:00",
                        "note", "Nộp báo cáo"
                ))
                .status("PENDING")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        pending.setId("confirmation-1");

        when(pendingActionRepository.findByIdAndUserId(pending.getId(), user.getId()))
                .thenReturn(Optional.of(pending));
        when(pendingActionRepository.save(any(TaskAssistantPendingAction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(messageReminderService.createReminder(any())).thenReturn(MessageReminderResponse.builder()
                .id("reminder-1")
                .messageId(message.getId())
                .remindAt("2026-07-28T09:00:00+07:00")
                .note("Nộp báo cáo")
                .build());

        TaskAssistantResponse result = service.confirm(pending.getId());

        assertEquals("COMPLETED", result.getStatus());
        assertEquals("reminder-1", result.getResult().get("reminderId"));
        verifyNoInteractions(restTemplate, channelTaskService);
    }

    private TaskAssistantRequest request(String prompt) {
        TaskAssistantRequest request = new TaskAssistantRequest();
        request.setConversationId(conversation.getId());
        request.setPrompt(prompt);
        return request;
    }
}
