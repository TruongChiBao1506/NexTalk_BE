package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.NotificationResponse;
import iuh.fit.se.nextalk_be.entity.Notification;
import iuh.fit.se.nextalk_be.service.NotificationService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification Management", description = "APIs for retrieving and managing user notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get all notifications for the current user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications() {
        List<NotificationResponse> notifications = notificationService.getMyNotifications();
        return ResponseEntity.ok(ApiResponse.success(notifications, "Notifications retrieved successfully"));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable("id") String id) {
        NotificationResponse response = notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Notification marked as read"));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count for the current user")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        long count = notificationService.countUnread();
        return ResponseEntity.ok(ApiResponse.success(count, "Unread count retrieved successfully"));
    }
}
