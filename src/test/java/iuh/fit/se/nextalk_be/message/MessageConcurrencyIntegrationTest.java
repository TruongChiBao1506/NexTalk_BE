package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.dto.request.MessageRequest;
import iuh.fit.se.nextalk_be.dto.request.PollVoteRequest;
import iuh.fit.se.nextalk_be.dto.request.ReactMessageRequest;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.MessageStatusRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.FCMService;
import iuh.fit.se.nextalk_be.service.LinkPreviewService;
import iuh.fit.se.nextalk_be.service.NotificationService;
import iuh.fit.se.nextalk_be.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class MessageConcurrencyIntegrationTest {

    @Autowired private MessageServiceImpl messageService;
    @Autowired private MessageRepository messageRepository;
    @Autowired private MessageStatusRepository messageStatusRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private SimpMessagingTemplate messagingTemplate;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private FCMService fcmService;
    @MockitoBean private LinkPreviewService linkPreviewService;

    private ExecutorService executor;
    private User firstUser;
    private User secondUser;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        when(linkPreviewService.containsPreviewableUrl(anyString())).thenReturn(false);
        messageStatusRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        firstUser = userRepository.save(User.builder()
                .email("concurrency-one@example.test")
                .username("concurrency-one")
                .password("test-password")
                .isVerified(true)
                .build());
        secondUser = userRepository.save(User.builder()
                .email("concurrency-two@example.test")
                .username("concurrency-two")
                .password("test-password")
                .isVerified(true)
                .build());
        conversation = conversationRepository.save(Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(Set.of(firstUser, secondUser))
                .build());
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        executor.shutdownNow();
    }

    @Test
    void concurrentRetriesWithSameClientMessageIdCreateOneMessage() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<MessageResponse> send = asUser(firstUser.getEmail(), () -> {
            start.await();
            return messageService.sendMessage(MessageRequest.builder()
                    .conversationId(conversation.getId())
                    .content("idempotency-test-payload")
                    .messageType("TEXT")
                    .clientMessageId("concurrent-client-message")
                    .build());
        });

        Future<MessageResponse> first = executor.submit(send);
        Future<MessageResponse> second = executor.submit(send);
        start.countDown();

        assertThat(first.get().getId()).isEqualTo(second.get().getId());
        assertThat(messageRepository.findAll()).hasSize(1);
        assertThat(messageStatusRepository.findAll()).hasSize(1);
    }

    @Test
    void concurrentReactionsFromDifferentUsersAreBothPreserved() throws Exception {
        Message message = messageRepository.save(Message.builder()
                .conversation(conversation)
                .conversationId(conversation.getId())
                .sender(firstUser)
                .senderId(firstUser.getId())
                .senderUsername(firstUser.getUsername())
                .content("reaction-test-payload")
                .messageType(MessageType.TEXT)
                .build());
        CountDownLatch start = new CountDownLatch(1);

        Future<MessageResponse> first = executor.submit(asUser(firstUser.getEmail(), () -> {
            start.await();
            return messageService.reactToMessage(message.getId(), new ReactMessageRequest("LIKE"));
        }));
        Future<MessageResponse> second = executor.submit(asUser(secondUser.getEmail(), () -> {
            start.await();
            return messageService.reactToMessage(message.getId(), new ReactMessageRequest("HEART"));
        }));
        start.countDown();
        first.get();
        second.get();

        Message stored = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(stored.getReactions())
                .extracting(reaction -> reaction.getUserId() + ":" + reaction.getEmoji())
                .containsExactlyInAnyOrder(
                        firstUser.getId() + ":LIKE",
                        secondUser.getId() + ":HEART");
    }

    @Test
    void concurrentPollVotesAreBothPreserved() throws Exception {
        Map<String, Object> option = new HashMap<>();
        option.put("id", "option-one");
        option.put("text", "Option one");
        option.put("voterIds", new ArrayList<String>());
        option.put("voters", new ArrayList<Map<String, Object>>());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("allowMultiple", false);
        metadata.put("locked", false);
        metadata.put("options", new ArrayList<>(List.of(option)));
        Message poll = messageRepository.save(Message.builder()
                .conversation(conversation)
                .conversationId(conversation.getId())
                .sender(firstUser)
                .senderId(firstUser.getId())
                .senderUsername(firstUser.getUsername())
                .content("poll-test-payload")
                .messageType(MessageType.POLL)
                .metadata(metadata)
                .build());
        CountDownLatch start = new CountDownLatch(1);

        Future<MessageResponse> first = executor.submit(asUser(firstUser.getEmail(), () -> {
            start.await();
            return messageService.votePoll(poll.getId(), new PollVoteRequest("option-one"));
        }));
        Future<MessageResponse> second = executor.submit(asUser(secondUser.getEmail(), () -> {
            start.await();
            return messageService.votePoll(poll.getId(), new PollVoteRequest("option-one"));
        }));
        start.countDown();
        first.get();
        second.get();

        Message stored = messageRepository.findById(poll.getId()).orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> storedOptions = (List<Map<String, Object>>) stored.getMetadata().get("options");
        @SuppressWarnings("unchecked")
        List<String> voterIds = (List<String>) storedOptions.get(0).get("voterIds");
        assertThat(voterIds).containsExactlyInAnyOrder(firstUser.getId(), secondUser.getId());
    }

    private <T> Callable<T> asUser(String username, Callable<T> action) {
        return () -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(username, "test-password", List.of()));
            try {
                return action.call();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }
}
