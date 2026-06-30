package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.response.NotificationResponse;
import iuh.fit.se.nextalk_be.entity.Notification;
import iuh.fit.se.nextalk_be.entity.NotificationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.NotificationRepository;
import iuh.fit.se.nextalk_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificationService {
    public NotificationResponse createAndSend(User recipient, NotificationType type, String content, String referenceId);
    public List<NotificationResponse> getMyNotifications();
    public NotificationResponse markAsRead(String notificationId);
    public long countUnread();
}
