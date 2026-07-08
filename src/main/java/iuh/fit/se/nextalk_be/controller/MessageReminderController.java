package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.CreateMessageReminderRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageReminderResponse;
import iuh.fit.se.nextalk_be.service.MessageReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reminders")
@Tag(name = "Message Reminders", description = "APIs for message reminders")
public class MessageReminderController {

    private final MessageReminderService messageReminderService;

    @GetMapping
    @Operation(summary = "Get reminders for the current user")
    public ResponseEntity<ApiResponse<List<MessageReminderResponse>>> getMyReminders() {
        return ResponseEntity.ok(ApiResponse.success(messageReminderService.getMyReminders(), "Reminders retrieved successfully"));
    }

    @PostMapping
    @Operation(summary = "Create a reminder for a message")
    public ResponseEntity<ApiResponse<MessageReminderResponse>> createReminder(
            @Valid @RequestBody CreateMessageReminderRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(messageReminderService.createReminder(request), "Reminder created successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a reminder")
    public ResponseEntity<ApiResponse<MessageReminderResponse>> deleteReminder(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(messageReminderService.deleteReminder(id), "Reminder deleted successfully"));
    }

    @PostMapping("/{id}/fire")
    @Operation(summary = "Mark a reminder as fired")
    public ResponseEntity<ApiResponse<MessageReminderResponse>> markReminderFired(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(messageReminderService.markReminderFired(id), "Reminder marked as fired"));
    }
}
