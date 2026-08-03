package iuh.fit.se.nextalk_be.notification;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageNotificationDispatchStatus;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.Notification;
import iuh.fit.se.nextalk_be.entity.NotificationDeliveryStatus;
import iuh.fit.se.nextalk_be.entity.NotificationPushKind;
import iuh.fit.se.nextalk_be.entity.NotificationPushPayload;
import iuh.fit.se.nextalk_be.entity.NotificationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.NotificationRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.FCMService;
import iuh.fit.se.nextalk_be.service.MessageNotificationDispatcher;
import iuh.fit.se.nextalk_be.service.NotificationOutboxWorker;
import iuh.fit.se.nextalk_be.service.NotificationService;
import iuh.fit.se.nextalk_be.service.PushDeliveryException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class NotificationOutboxIntegrationTest {
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationOutboxWorker outboxWorker;
    @Autowired private MessageNotificationDispatcher messageDispatcher;
    @Autowired private MongoTemplate mongoTemplate;

    @MockitoBean private FCMService fcmService;
    @MockitoBean private SimpMessagingTemplate messagingTemplate;

    private User recipient;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
        reset(fcmService, messagingTemplate);
        recipient = userRepository.save(User.builder()
                .email("outbox-recipient")
                .username("outbox-recipient")
                .fcmTokens(List.of("outbox-token"))
                .build());
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void expiredProcessingLeaseIsRecoveredAfterRestart() {
        Notification notification = saveGenericNotification(NotificationDeliveryStatus.PROCESSING);
        notification.setDeliveryLeaseId("abandoned-lease");
        notification.setDeliveryLeaseUntil(LocalDateTime.now().minusSeconds(1));
        notificationRepository.save(notification);

        assertThat(outboxWorker.processBatch()).isEqualTo(1);

        Notification delivered = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(delivered.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(delivered.getDeliveryAttempts()).isEqualTo(1);
        assertThat(delivered.getDeliveredAt()).isNotNull();
        verify(fcmService).sendPushNotificationToTokens(anyList(), anyString(), anyString());
    }

    @Test
    void transientProviderFailureRetriesAndEventuallySends() {
        Notification notification = saveGenericNotification(NotificationDeliveryStatus.PENDING);
        doThrow(PushDeliveryException.retryable("FCM_UNAVAILABLE", null))
                .doNothing()
                .when(fcmService).sendPushNotificationToTokens(anyList(), anyString(), anyString());

        assertThat(outboxWorker.processBatch()).isEqualTo(1);
        Notification waiting = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(waiting.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(waiting.getNextDeliveryAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(waiting.getLastDeliveryErrorCode()).isEqualTo("FCM_UNAVAILABLE");

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(notification.getId())),
                new Update().set("nextDeliveryAttemptAt", LocalDateTime.now().minusSeconds(1)),
                Notification.class);

        assertThat(outboxWorker.processBatch()).isEqualTo(1);
        Notification delivered = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(delivered.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(delivered.getDeliveryAttempts()).isEqualTo(2);
        verify(fcmService, times(2)).sendPushNotificationToTokens(anyList(), anyString(), anyString());
    }

    @Test
    void permanentFailureMovesNotificationToDeadLetter() {
        Notification notification = saveGenericNotification(NotificationDeliveryStatus.PENDING);
        doThrow(PushDeliveryException.permanent("FCM_INVALID_ARGUMENT", null))
                .when(fcmService).sendPushNotificationToTokens(anyList(), anyString(), anyString());

        outboxWorker.processBatch();

        Notification failed = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(failed.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(failed.getDeadLetteredAt()).isNotNull();
        assertThat(failed.getLastDeliveryErrorCode()).isEqualTo("FCM_INVALID_ARGUMENT");
    }

    @Test
    void chatOutboxCreationIsIdempotentForMessageAndRecipient() {
        var first = notificationService.createChatNotification(
                recipient, NotificationType.NEW_MESSAGE, "generic notification",
                "message-id", "conversation-id", "Conversation",
                "sender-id", "Sender", "", "generic preview");
        var second = notificationService.createChatNotification(
                recipient, NotificationType.NEW_MESSAGE, "generic notification",
                "message-id", "conversation-id", "Conversation",
                "sender-id", "Sender", "", "generic preview");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(notificationRepository.findAll()).hasSize(1);
        assertThat(notificationRepository.findAll().get(0).getDeliveryStatus())
                .isEqualTo(NotificationDeliveryStatus.PENDING);
    }

    @Test
    void chatOutboxUsesTheRecipientsCurrentRegisteredToken() {
        notificationService.createChatNotification(
                recipient, NotificationType.NEW_MESSAGE, "generic notification",
                "message-id", "conversation-id", "Conversation",
                "sender-id", "Sender", "", "generic preview");

        assertThat(outboxWorker.processBatch()).isEqualTo(1);

        Notification delivered = notificationRepository.findAll().get(0);
        assertThat(delivered.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        verify(fcmService).sendChatPushNotificationToTokens(
                org.mockito.ArgumentMatchers.eq(List.of("outbox-token")),
                org.mockito.ArgumentMatchers.eq("message-id"),
                org.mockito.ArgumentMatchers.eq("conversation-id"),
                org.mockito.ArgumentMatchers.eq("Conversation"),
                org.mockito.ArgumentMatchers.eq("sender-id"),
                org.mockito.ArgumentMatchers.eq("Sender"),
                org.mockito.ArgumentMatchers.eq(""),
                org.mockito.ArgumentMatchers.eq("generic preview"));
    }

    @Test
    void missingDeviceTokenRetriesInsteadOfReportingFalseSuccess() {
        recipient.setFcmTokens(List.of());
        userRepository.save(recipient);
        Notification notification = saveGenericNotification(NotificationDeliveryStatus.PENDING);

        assertThat(outboxWorker.processBatch()).isEqualTo(1);

        Notification waiting = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(waiting.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(waiting.getLastDeliveryErrorCode()).isEqualTo("NO_REGISTERED_PUSH_TOKEN");
        assertThat(waiting.getNextDeliveryAttemptAt()).isAfter(LocalDateTime.now());
        verify(fcmService, never()).sendPushNotificationToTokens(anyList(), anyString(), anyString());
    }

    @Test
    void savedMessageWithAbandonedLeaseCreatesOutboxAfterRestart() {
        User sender = userRepository.save(User.builder()
                .email("outbox-sender")
                .username("outbox-sender")
                .build());
        Conversation conversation = conversationRepository.save(Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(Set.of(sender, recipient))
                .build());
        Message message = messageRepository.save(Message.builder()
                .conversation(conversation)
                .conversationId(conversation.getId())
                .sender(sender)
                .senderId(sender.getId())
                .senderUsername(sender.getUsername())
                .content("generic message")
                .messageType(MessageType.TEXT)
                .metadata(Map.of())
                .notificationDispatchStatus(MessageNotificationDispatchStatus.PROCESSING)
                .notificationDispatchLeaseId("abandoned-dispatch")
                .notificationDispatchLeaseUntil(LocalDateTime.now().minusSeconds(1))
                .build());

        messageDispatcher.dispatchNow(message.getId());

        Message recovered = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(recovered.getNotificationDispatchStatus())
                .isEqualTo(MessageNotificationDispatchStatus.COMPLETE);
        assertThat(notificationRepository.findAll()).hasSize(1);
        assertThat(notificationRepository.findAll().get(0).getDeliveryStatus())
                .isEqualTo(NotificationDeliveryStatus.PENDING);
    }

    private Notification saveGenericNotification(NotificationDeliveryStatus status) {
        return notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(NotificationType.FRIEND_REQUEST)
                .content("generic notification")
                .pushIdempotencyKey("outbox:" + new org.bson.types.ObjectId().toHexString())
                .deliveryStatus(status)
                .nextDeliveryAttemptAt(LocalDateTime.now().minusSeconds(1))
                .pushPayload(NotificationPushPayload.builder()
                        .kind(NotificationPushKind.GENERIC)
                        .title("NexTalk")
                        .body("generic notification")
                        .build())
                .build());
    }
}
