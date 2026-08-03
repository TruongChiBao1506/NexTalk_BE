package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.entity.Notification;
import iuh.fit.se.nextalk_be.entity.NotificationActionStatus;
import iuh.fit.se.nextalk_be.entity.NotificationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.NotificationRepository;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserService userService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private NotificationServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, userService, messagingTemplate);
        user = User.builder().username("member").email("member@example.com").build();
        user.setId("user-1");
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void readingNotificationDoesNotResolveItsActionItem() {
        Notification notification = actionableNotification(NotificationActionStatus.PENDING);
        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(notification));

        var response = service.markAsRead("notification-1");

        assertThat(response.isRead()).isTrue();
        assertThat(response.getActionStatus()).isEqualTo("PENDING");
        assertThat(notification.getActionStatus()).isEqualTo(NotificationActionStatus.PENDING);
    }

    @Test
    void resolvingActionItemDoesNotChangeReadState() {
        Notification notification = actionableNotification(NotificationActionStatus.PENDING);
        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(notification));

        var response = service.updateActionStatus(
                "notification-1",
                NotificationActionStatus.RESOLVED,
                null
        );

        assertThat(response.isRead()).isFalse();
        assertThat(response.getActionStatus()).isEqualTo("RESOLVED");
    }

    @Test
    void snoozedPendingItemIsHiddenUntilItsReminderTime() {
        Notification visible = actionableNotification(NotificationActionStatus.PENDING);
        Notification snoozed = actionableNotification(NotificationActionStatus.PENDING);
        snoozed.setId("notification-2");
        snoozed.setSnoozedUntil(LocalDateTime.now().plusDays(1));
        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(notificationRepository.findActionItemsByRecipientUser("user-1", null))
                .thenReturn(List.of(visible, snoozed));

        var responses = service.getMyActionItems(NotificationActionStatus.PENDING);

        assertThat(responses).extracting("id").containsExactly("notification-1");
    }

    private Notification actionableNotification(NotificationActionStatus status) {
        Notification notification = Notification.builder()
                .recipient(user)
                .type(NotificationType.MENTION)
                .content("Bạn được nhắc đến")
                .referenceId("conversation-1")
                .isRead(false)
                .actionStatus(status)
                .build();
        notification.setId("notification-1");
        return notification;
    }
}
