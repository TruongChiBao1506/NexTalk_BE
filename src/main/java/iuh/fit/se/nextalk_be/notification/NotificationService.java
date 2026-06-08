package iuh.fit.se.nextalk_be.notification;

import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.notification.dto.NotificationResponse;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Create, persist, and push a notification to a specific recipient via WebSocket.
     */
    @Transactional
    public NotificationResponse createAndSend(User recipient, NotificationType type, String content, String referenceId) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .content(content)
                .referenceId(referenceId)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = mapToResponse(saved);

        // Push realtime to recipient's private notification queue
        messagingTemplate.convertAndSendToUser(
                recipient.getUsername(),
                "/queue/notifications",
                response
        );

        return response;
    }

    /**
     * Get all notifications for the currently authenticated user, newest first.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Mark a single notification as read. Only the owner may mark their own notifications.
     */
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (!notification.getRecipient().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Notification not found: " + notificationId);
        }

        notification.setRead(true);
        notification.setUpdatedAt(LocalDateTime.now());
        Notification saved = notificationRepository.save(notification);
        return mapToResponse(saved);
    }

    /**
     * Count unread notifications for the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public long countUnread() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        return notificationRepository.countByRecipientIdAndIsReadFalse(currentUser.getId());
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .content(notification.getContent())
                .referenceId(notification.getReferenceId())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt() != null ? notification.getCreatedAt() : LocalDateTime.now())
                .build();
    }
}
