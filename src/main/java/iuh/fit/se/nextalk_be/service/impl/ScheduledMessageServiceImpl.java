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
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ScheduledMessageServiceImpl implements ScheduledMessageService {
    private static final String WAKEUP_CHANNEL = "nextalk:scheduled-message:wakeup";
    private static final String DISPATCH_LOCK_KEY = "nextalk:scheduled-message:dispatch-lock";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final ScheduledMessageRepository repository;
    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final MessageService messageService;
    private final StringRedisTemplate redisTemplate;
    private final String schedulerInstanceId = UUID.randomUUID().toString();
    private final ScheduledExecutorService schedulerExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "scheduled-message-dispatcher");
        thread.setDaemon(true);
        return thread;
    });
    private final Object schedulerMonitor = new Object();
    private ScheduledFuture<?> nextDispatchTask;
    private LocalDateTime armedFor;

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
        ScheduledMessage saved = repository.save(scheduled);
        publishWakeup();
        armFromDatabase();
        return map(saved);
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
        ScheduledMessage saved = repository.save(scheduled);
        publishWakeup();
        armFromDatabase();
        return map(saved);
    }

    @Override
    public void dispatchDueMessages() {
        if (!acquireDispatchLock()) {
            scheduleRefresh(Duration.ofSeconds(2));
            return;
        }
        try {
        List<ScheduledMessage> due = repository
                .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        ScheduledMessageStatus.PENDING,
                        LocalDateTime.now()
                );
        for (ScheduledMessage scheduled : due.stream().limit(100).toList()) {
            dispatch(scheduled);
        }
        } finally {
            releaseDispatchLock();
            publishWakeup();
            armFromDatabase();
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
            if (scheduled.getStatus() == ScheduledMessageStatus.PENDING) {
                scheduled.setScheduledAt(LocalDateTime.now().plusSeconds(30));
            }
        }
        repository.save(scheduled);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restorePendingSchedules() {
        armFromDatabase();
    }

    public void onExternalWakeup() {
        armFromDatabase();
    }

    private void armFromDatabase() {
        repository.findFirstByStatusOrderByScheduledAtAsc(ScheduledMessageStatus.PENDING)
                .ifPresentOrElse(
                        next -> armFor(next.getScheduledAt()),
                        this::cancelArmedTask
                );
    }

    private void armFor(LocalDateTime scheduledAt) {
        synchronized (schedulerMonitor) {
            if (nextDispatchTask != null && !nextDispatchTask.isDone()
                    && armedFor != null && !scheduledAt.isBefore(armedFor)) {
                return;
            }
            if (nextDispatchTask != null) {
                nextDispatchTask.cancel(false);
            }
            long delayMillis = Math.max(
                    0,
                    Duration.between(LocalDateTime.now(), scheduledAt).toMillis()
            );
            armedFor = scheduledAt;
            nextDispatchTask = schedulerExecutor.schedule(() -> {
                synchronized (schedulerMonitor) {
                    nextDispatchTask = null;
                    armedFor = null;
                }
                dispatchDueMessages();
            }, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void scheduleRefresh(Duration delay) {
        synchronized (schedulerMonitor) {
            if (nextDispatchTask != null) {
                nextDispatchTask.cancel(false);
            }
            armedFor = LocalDateTime.now().plus(delay);
            nextDispatchTask = schedulerExecutor.schedule(() -> {
                synchronized (schedulerMonitor) {
                    nextDispatchTask = null;
                    armedFor = null;
                }
                armFromDatabase();
            }, delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void cancelArmedTask() {
        synchronized (schedulerMonitor) {
            if (nextDispatchTask != null) {
                nextDispatchTask.cancel(false);
            }
            nextDispatchTask = null;
            armedFor = null;
        }
    }

    private void publishWakeup() {
        try {
            redisTemplate.convertAndSend(WAKEUP_CHANNEL, "refresh");
        } catch (Exception ignored) {
            // Local scheduling remains available when Redis is temporarily unavailable.
        }
    }

    private boolean acquireDispatchLock() {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    DISPATCH_LOCK_KEY,
                    schedulerInstanceId,
                    Duration.ofSeconds(30)
            );
            return Boolean.TRUE.equals(acquired);
        } catch (Exception ignored) {
            return true;
        }
    }

    private void releaseDispatchLock() {
        try {
            redisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    Collections.singletonList(DISPATCH_LOCK_KEY),
                    schedulerInstanceId
            );
        } catch (Exception ignored) {
            // The lock expires automatically.
        }
    }

    @PreDestroy
    public void shutdownScheduler() {
        cancelArmedTask();
        schedulerExecutor.shutdownNow();
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
