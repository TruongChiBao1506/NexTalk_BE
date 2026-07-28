package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.response.NotificationResponse;
import iuh.fit.se.nextalk_be.entity.NotificationActionStatus;
import iuh.fit.se.nextalk_be.entity.NotificationType;
import iuh.fit.se.nextalk_be.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationService {
    NotificationResponse createAndSend(User recipient, NotificationType type, String content, String referenceId);
    NotificationResponse createAndSend(User recipient, NotificationType type, String content, String referenceId, String secondaryReferenceId);
    List<NotificationResponse> getMyNotifications();
    NotificationResponse markAsRead(String notificationId);
    long countUnread();
    List<NotificationResponse> getMyActionItems(NotificationActionStatus status);
    NotificationResponse updateActionStatus(String notificationId, NotificationActionStatus status, LocalDateTime snoozedUntil);
    long countPendingActions();
}
