package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.NotificationService;

import iuh.fit.se.nextalk_be.dto.response.NotificationResponse;
import iuh.fit.se.nextalk_be.entity.Notification;
import iuh.fit.se.nextalk_be.entity.NotificationActionStatus;
import iuh.fit.se.nextalk_be.entity.NotificationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.NotificationRepository;
import iuh.fit.se.nextalk_be.service.FCMService;
import iuh.fit.se.nextalk_be.service.UserService;


import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FCMService fcmService;

    /**
     * Create, persist, and push a notification to a specific recipient via WebSocket.
     */
    // @Transactional
    public NotificationResponse createAndSend(User recipient, NotificationType type, String content, String referenceId) {
        return createAndSend(recipient, type, content, referenceId, null);
    }

    @Override
    public NotificationResponse createAndSend(
            User recipient,
            NotificationType type,
            String content,
            String referenceId,
            String secondaryReferenceId
    ) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .content(content)
                .referenceId(referenceId)
                .secondaryReferenceId(secondaryReferenceId)
                .isRead(false)
                .actionStatus(isActionableType(type) ? NotificationActionStatus.PENDING : null)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = mapToResponse(saved);

        // Push realtime to recipient's private notification queue
        messagingTemplate.convertAndSendToUser(
                recipient.getUsername(),
                "/queue/notifications",
                response
        );

        if (shouldSendPush(type)) {
            fcmService.sendPushNotificationToTokens(recipient.getFcmTokens(), "NexTalk", content);
        }

        return response;
    }

    /**
     * Get all notifications for the currently authenticated user, newest first.
     */
    // @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        String userId = currentUser.getId();
        org.bson.types.ObjectId userObjectId = (userId != null && org.bson.types.ObjectId.isValid(userId))
                ? new org.bson.types.ObjectId(userId)
                : null;

        List<Notification> notifications = notificationRepository.findTop50ByRecipientUser(userId, userObjectId);
        if (notifications.isEmpty()) {
            notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
        }

        return notifications.stream()
                .limit(50)
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Mark a single notification as read. Only the owner may mark their own notifications.
     */
    // @Transactional
    public NotificationResponse markAsRead(String notificationId) {
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
    // @Transactional(readOnly = true)
    public long countUnread() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        String userId = currentUser.getId();
        org.bson.types.ObjectId userObjectId = (userId != null && org.bson.types.ObjectId.isValid(userId))
                ? new org.bson.types.ObjectId(userId)
                : null;

        long count = notificationRepository.countUnreadByRecipientUser(userId, userObjectId);
        if (count == 0) {
            count = notificationRepository.countByRecipientIdAndIsReadFalse(userId);
        }
        return count;
    }

    @Override
    public List<NotificationResponse> getMyActionItems(NotificationActionStatus status) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        String userId = currentUser.getId();
        org.bson.types.ObjectId userObjectId = (userId != null && org.bson.types.ObjectId.isValid(userId))
                ? new org.bson.types.ObjectId(userId)
                : null;
        LocalDateTime now = LocalDateTime.now();

        return notificationRepository.findActionItemsByRecipientUser(userId, userObjectId).stream()
                .filter(notification -> status == null || notification.getActionStatus() == status)
                .filter(notification -> status != NotificationActionStatus.PENDING
                        || notification.getSnoozedUntil() == null
                        || !notification.getSnoozedUntil().isAfter(now))
                .limit(100)
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public NotificationResponse updateActionStatus(
            String notificationId,
            NotificationActionStatus status,
            LocalDateTime snoozedUntil
    ) {
        if (status == null) {
            throw new IllegalArgumentException("Action status is required");
        }

        User currentUser = userService.getCurrentAuthenticatedUser();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (notification.getRecipient() == null
                || !notification.getRecipient().getId().equals(currentUser.getId())
                || notification.getActionStatus() == null) {
            throw new ResourceNotFoundException("Action item not found: " + notificationId);
        }

        notification.setActionStatus(status);
        notification.setSnoozedUntil(status == NotificationActionStatus.PENDING ? snoozedUntil : null);
        notification.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(notificationRepository.save(notification));
    }

    @Override
    public long countPendingActions() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        String userId = currentUser.getId();
        org.bson.types.ObjectId userObjectId = (userId != null && org.bson.types.ObjectId.isValid(userId))
                ? new org.bson.types.ObjectId(userId)
                : null;
        LocalDateTime now = LocalDateTime.now();

        return notificationRepository.findActionItemsByRecipientUser(userId, userObjectId).stream()
                .filter(notification -> notification.getActionStatus() == NotificationActionStatus.PENDING)
                .filter(notification -> notification.getSnoozedUntil() == null
                        || !notification.getSnoozedUntil().isAfter(now))
                .count();
    }

    private boolean shouldSendPush(NotificationType type) {
        return type != NotificationType.NEW_MESSAGE
                && type != NotificationType.MENTION
                && type != NotificationType.REMINDER
                && type != NotificationType.TASK_ASSIGNED
                && type != NotificationType.TASK_DUE;
    }

    private boolean isActionableType(NotificationType type) {
        return type == NotificationType.MENTION
                || type == NotificationType.FRIEND_REQUEST
                || type == NotificationType.GROUP_INVITE
                || type == NotificationType.CHAT_REQUEST
                || type == NotificationType.REMINDER
                || type == NotificationType.TASK_ASSIGNED
                || type == NotificationType.TASK_DUE
                || type == NotificationType.MISSED_CALL;
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .content(notification.getContent())
                .referenceId(notification.getReferenceId())
                .secondaryReferenceId(notification.getSecondaryReferenceId())
                .isRead(notification.isRead())
                .actionStatus(notification.getActionStatus() != null ? notification.getActionStatus().name() : null)
                .snoozedUntil(notification.getSnoozedUntil())
                .createdAt(notification.getCreatedAt() != null ? notification.getCreatedAt() : LocalDateTime.now())
                .build();
    }
}
