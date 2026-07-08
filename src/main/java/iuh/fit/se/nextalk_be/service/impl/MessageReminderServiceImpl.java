package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.CreateMessageReminderRequest;
import iuh.fit.se.nextalk_be.dto.response.MessageReminderResponse;
import iuh.fit.se.nextalk_be.entity.*;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.MessageReminderRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.FCMService;
import iuh.fit.se.nextalk_be.service.MessageReminderService;
import iuh.fit.se.nextalk_be.service.NotificationService;
import iuh.fit.se.nextalk_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageReminderServiceImpl implements MessageReminderService {

    private final MessageReminderRepository messageReminderRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final FCMService fcmService;

    @Override
    public List<MessageReminderResponse> getMyReminders() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        return messageReminderRepository.findByUserIdOrderByRemindAtAsc(currentUser.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public MessageReminderResponse createReminder(CreateMessageReminderRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message message = messageRepository.findById(request.getMessageId())
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + request.getMessageId()));

        Conversation conversation = message.getConversation();
        boolean isMember = conversation.getMembers().stream()
                .anyMatch(member -> member.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }
        if (message.isRecalled()) {
            throw new BadRequestException("Cannot create a reminder for a recalled message");
        }
        if (message.getDeletedByUsers() != null && message.getDeletedByUsers().contains(currentUser.getId())) {
            throw new BadRequestException("Cannot create a reminder for a message deleted for you");
        }

        LocalDateTime remindAt = parseReminderTime(request.getRemindAt());
        if (!remindAt.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Reminder time must be in the future");
        }

        MessageReminder reminder = MessageReminder.builder()
                .user(currentUser)
                .conversation(conversation)
                .message(message)
                .remindAt(remindAt)
                .note(trimToLength(request.getNote(), 180))
                .messagePreview(buildMessagePreview(message))
                .senderUsername(message.getSender() != null ? message.getSender().getUsername() : "NexTalk")
                .status(MessageReminderStatus.PENDING)
                .build();

        return mapToResponse(messageReminderRepository.save(reminder));
    }

    @Override
    public MessageReminderResponse deleteReminder(String reminderId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        MessageReminder reminder = messageReminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found: " + reminderId));

        if (!reminder.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Reminder not found: " + reminderId);
        }

        reminder.setStatus(MessageReminderStatus.DELETED);
        reminder.setDeletedAt(LocalDateTime.now());
        reminder.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(messageReminderRepository.save(reminder));
    }

    @Override
    public MessageReminderResponse markReminderFired(String reminderId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        MessageReminder reminder = messageReminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found: " + reminderId));

        if (!reminder.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Reminder not found: " + reminderId);
        }

        if (reminder.getStatus() == MessageReminderStatus.PENDING) {
            reminder.setStatus(MessageReminderStatus.FIRED);
            reminder.setFiredAt(LocalDateTime.now());
            reminder.setUpdatedAt(LocalDateTime.now());
            reminder = messageReminderRepository.save(reminder);
        }
        return mapToResponse(reminder);
    }

    @Scheduled(fixedDelayString = "${app.reminders.scheduler-delay-ms:30000}")
    @Override
    public void dispatchDueReminders() {
        List<MessageReminder> dueReminders = messageReminderRepository
                .findByStatusAndRemindAtLessThanEqual(MessageReminderStatus.PENDING, LocalDateTime.now())
                .stream()
                .sorted(Comparator.comparing(MessageReminder::getRemindAt))
                .toList();

        for (MessageReminder reminder : dueReminders) {
            try {
                fireReminder(reminder);
            } catch (Exception ignored) {
                // Keep the scheduler alive; the reminder remains pending for a future retry.
            }
        }
    }

    private void fireReminder(MessageReminder reminder) {
        User recipient = userRepository.findById(reminder.getUser().getId()).orElse(reminder.getUser());
        String body = reminder.getNote() != null && !reminder.getNote().isBlank()
                ? reminder.getNote()
                : reminder.getSenderUsername() + ": " + reminder.getMessagePreview();

        notificationService.createAndSend(
                recipient,
                NotificationType.REMINDER,
                "NexTalk nhac hen: " + body,
                reminder.getConversation().getId()
        );

        if (recipient.getFcmTokens() != null && !recipient.getFcmTokens().isEmpty()) {
            fcmService.sendPushNotificationToTokens(recipient.getFcmTokens(), "NexTalk nhac hen", body);
        }

        reminder.setStatus(MessageReminderStatus.FIRED);
        reminder.setFiredAt(LocalDateTime.now());
        reminder.setUpdatedAt(LocalDateTime.now());
        messageReminderRepository.save(reminder);
    }

    private LocalDateTime parseReminderTime(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Reminder time is required");
        }

        try {
            return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Fall through.
        }

        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Fall through.
        }

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            throw new BadRequestException("Invalid reminder time");
        }
    }

    private String buildMessagePreview(Message message) {
        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            MessageAttachment attachment = message.getAttachments().get(0);
            if ("IMAGE".equalsIgnoreCase(attachment.getType())) return "[Hinh anh]";
            if ("VIDEO".equalsIgnoreCase(attachment.getType())) return "[Video]";
            if ("AUDIO".equalsIgnoreCase(attachment.getType())) return "[Tin nhan thoai]";
            return attachment.getName() != null && !attachment.getName().isBlank() ? attachment.getName() : "[Tep dinh kem]";
        }

        String preview = message.getContent() != null ? message.getContent() : "";
        preview = preview
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
        if (preview.length() > 140) {
            preview = preview.substring(0, 137) + "...";
        }
        return preview.isBlank() ? "[Tin nhan]" : preview;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private MessageReminderResponse mapToResponse(MessageReminder reminder) {
        return MessageReminderResponse.builder()
                .id(reminder.getId())
                .messageId(reminder.getMessage().getId())
                .conversationId(reminder.getConversation().getId())
                .senderUsername(reminder.getSenderUsername())
                .messagePreview(reminder.getMessagePreview())
                .remindAt(reminder.getRemindAt().atZone(ZoneId.systemDefault()).toInstant().toString())
                .note(reminder.getNote())
                .status(reminder.getStatus().name())
                .createdAt(reminder.getCreatedAt())
                .firedAt(reminder.getFiredAt())
                .deletedAt(reminder.getDeletedAt())
                .build();
    }
}
