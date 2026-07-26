package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.MessageRequest;
import iuh.fit.se.nextalk_be.dto.request.ScheduleMessageRequest;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.dto.response.ScheduledMessageResponse;
import iuh.fit.se.nextalk_be.entity.*;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.ScheduledMessageRepository;
import iuh.fit.se.nextalk_be.service.MessageService;
import iuh.fit.se.nextalk_be.service.ScheduledMessageService;
import iuh.fit.se.nextalk_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduledMessageServiceImpl implements ScheduledMessageService {
    private final ScheduledMessageRepository repository;
    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final MessageService messageService;

    @Override
    public ScheduledMessageResponse schedule(ScheduleMessageRequest request) {
        User sender = userService.getCurrentAuthenticatedUser();
        MessageRequest payload = request.getMessage();
        boolean hasContent = payload.getContent() != null && !payload.getContent().trim().isEmpty();
        boolean hasAttachments = payload.getAttachments() != null && !payload.getAttachments().isEmpty();
        if (!hasContent && !hasAttachments) {
            throw new BadRequestException("Message content or attachments are required");
        }
        Conversation conversation = conversationRepository.findById(payload.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        boolean member = conversation.getMembers().stream()
                .anyMatch(item -> item.getId().equals(sender.getId()));
        if (!member) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        LocalDateTime scheduledAt = parseTime(request.getScheduledAt());
        if (!scheduledAt.isAfter(LocalDateTime.now().plusSeconds(10))) {
            throw new BadRequestException("Scheduled time must be at least 10 seconds in the future");
        }
        if (scheduledAt.isAfter(LocalDateTime.now().plusDays(365))) {
            throw new BadRequestException("Messages can only be scheduled up to one year ahead");
        }

        var metadata = payload.getMetadata() == null
                ? new HashMap<String, Object>()
                : new HashMap<>(payload.getMetadata());
        if (request.isSilent()) {
            metadata.put("silent", true);
        }
        payload.setMetadata(metadata);
        if (payload.getClientMessageId() == null || payload.getClientMessageId().isBlank()) {
            payload.setClientMessageId("scheduled-" + java.util.UUID.randomUUID());
        }

        ScheduledMessage scheduled = ScheduledMessage.builder()
                .sender(sender)
                .payload(payload)
                .scheduledAt(scheduledAt)
                .status(ScheduledMessageStatus.PENDING)
                .build();
        return map(repository.save(scheduled));
    }

    @Override
    public List<ScheduledMessageResponse> getPending() {
        User sender = userService.getCurrentAuthenticatedUser();
        return repository.findBySenderIdAndStatusOrderByScheduledAtAsc(
                        sender.getId(),
                        ScheduledMessageStatus.PENDING
                ).stream().map(this::map).toList();
    }

    @Override
    public ScheduledMessageResponse cancel(String scheduledMessageId) {
        User sender = userService.getCurrentAuthenticatedUser();
        ScheduledMessage scheduled = repository.findById(scheduledMessageId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled message not found"));
        if (!scheduled.getSender().getId().equals(sender.getId())) {
            throw new ResourceNotFoundException("Scheduled message not found");
        }
        if (scheduled.getStatus() != ScheduledMessageStatus.PENDING) {
            throw new BadRequestException("Only pending scheduled messages can be cancelled");
        }
        scheduled.setStatus(ScheduledMessageStatus.CANCELLED);
        scheduled.setCancelledAt(LocalDateTime.now());
        return map(repository.save(scheduled));
    }

    @Override
    @Scheduled(fixedDelayString = "${app.messages.scheduled-delay-ms:10000}")
    public void dispatchDueMessages() {
        List<ScheduledMessage> due = repository
                .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        ScheduledMessageStatus.PENDING,
                        LocalDateTime.now()
                );
        for (ScheduledMessage scheduled : due.stream().limit(100).toList()) {
            dispatch(scheduled);
        }
    }

    private void dispatch(ScheduledMessage scheduled) {
        scheduled.setStatus(ScheduledMessageStatus.PROCESSING);
        scheduled.setAttempts(scheduled.getAttempts() + 1);
        repository.save(scheduled);
        try {
            User sender = scheduled.getSender();
            MessageResponse response = messageService.sendMessage(scheduled.getPayload(), sender.getEmail());
            scheduled.setStatus(ScheduledMessageStatus.SENT);
            scheduled.setSentMessageId(response.getId());
            scheduled.setSentAt(LocalDateTime.now());
            scheduled.setLastError(null);
        } catch (Exception exception) {
            scheduled.setLastError(exception.getMessage());
            scheduled.setStatus(scheduled.getAttempts() >= 5
                    ? ScheduledMessageStatus.FAILED
                    : ScheduledMessageStatus.PENDING);
        }
        repository.save(scheduled);
    }

    private LocalDateTime parseTime(String value) {
        try {
            return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException second) {
                throw new BadRequestException("Invalid scheduled time");
            }
        }
    }

    private ScheduledMessageResponse map(ScheduledMessage item) {
        MessageRequest payload = item.getPayload();
        boolean silent = payload.getMetadata() != null
                && Boolean.TRUE.equals(payload.getMetadata().get("silent"));
        return ScheduledMessageResponse.builder()
                .id(item.getId())
                .conversationId(payload.getConversationId())
                .content(payload.getContent())
                .scheduledAt(item.getScheduledAt().atZone(ZoneId.systemDefault()).toInstant().toString())
                .silent(silent)
                .status(item.getStatus().name())
                .sentMessageId(item.getSentMessageId())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
