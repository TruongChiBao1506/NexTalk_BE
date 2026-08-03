package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.Notification;
import iuh.fit.se.nextalk_be.entity.NotificationDeliveryStatus;
import iuh.fit.se.nextalk_be.entity.NotificationPushKind;
import iuh.fit.se.nextalk_be.entity.NotificationPushPayload;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class NotificationOutboxWorker {
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final FCMService fcmService;
    private final NotificationOutboxMetrics metrics;
    private final boolean schedulerEnabled;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration leaseDuration;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
    private final double jitterRatio;

    public NotificationOutboxWorker(
            MongoTemplate mongoTemplate,
            UserRepository userRepository,
            FCMService fcmService,
            NotificationOutboxMetrics metrics,
            @Value("${app.notification-outbox.worker-enabled:true}") boolean schedulerEnabled,
            @Value("${app.notification-outbox.batch-size:50}") int batchSize,
            @Value("${app.notification-outbox.max-attempts:8}") int maxAttempts,
            @Value("${app.notification-outbox.lease-duration:30s}") Duration leaseDuration,
            @Value("${app.notification-outbox.base-backoff:2s}") Duration baseBackoff,
            @Value("${app.notification-outbox.max-backoff:15m}") Duration maxBackoff,
            @Value("${app.notification-outbox.jitter-ratio:0.20}") double jitterRatio
    ) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.fcmService = fcmService;
        this.metrics = metrics;
        this.schedulerEnabled = schedulerEnabled;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.leaseDuration = leaseDuration;
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;
        this.jitterRatio = Math.max(0.0, Math.min(jitterRatio, 0.90));
    }

    @Scheduled(fixedDelayString = "${app.notification-outbox.poll-delay-ms:1000}")
    public void runScheduledBatch() {
        if (schedulerEnabled) {
            processBatch();
        }
    }

    public int processBatch() {
        int processed = 0;
        try {
            while (processed < batchSize) {
                Notification claimed = claimNext(LocalDateTime.now());
                if (claimed == null) {
                    break;
                }
                deliver(claimed);
                processed++;
            }
        } finally {
            metrics.refreshQueueDepth();
        }
        return processed;
    }

    private Notification claimNext(LocalDateTime now) {
        Criteria duePending = new Criteria().andOperator(
                Criteria.where("deliveryStatus").is(NotificationDeliveryStatus.PENDING),
                new Criteria().orOperator(
                        Criteria.where("nextDeliveryAttemptAt").lte(now),
                        Criteria.where("nextDeliveryAttemptAt").exists(false),
                        Criteria.where("nextDeliveryAttemptAt").is(null)));
        Criteria expiredLease = new Criteria().andOperator(
                Criteria.where("deliveryStatus").is(NotificationDeliveryStatus.PROCESSING),
                Criteria.where("deliveryLeaseUntil").lte(now));
        Query query = new Query(new Criteria().orOperator(duePending, expiredLease))
                .with(Sort.by(Sort.Direction.ASC, "nextDeliveryAttemptAt", "createdAt", "_id"));
        String leaseId = UUID.randomUUID().toString();
        Update update = new Update()
                .set("deliveryStatus", NotificationDeliveryStatus.PROCESSING)
                .set("deliveryLeaseUntil", now.plus(leaseDuration))
                .set("deliveryLeaseId", leaseId)
                .set("deliveryStartedAt", now)
                .inc("deliveryAttempts", 1)
                .set("updatedAt", now);
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Notification.class);
    }

    private void deliver(Notification notification) {
        metrics.recordAttempt();
        try {
            User recipient = notification.getRecipient() == null
                    ? null
                    : userRepository.findById(notification.getRecipient().getId()).orElse(null);
            if (recipient == null) {
                throw PushDeliveryException.permanent("RECIPIENT_NOT_FOUND", null);
            }
            NotificationPushPayload payload = notification.getPushPayload();
            if (payload == null || payload.getKind() == null) {
                throw PushDeliveryException.permanent("PAYLOAD_INVALID", null);
            }
            List<String> pushTokens = recipient.getFcmTokens() == null
                    ? List.of()
                    : recipient.getFcmTokens().stream()
                    .filter(token -> token != null && !token.isBlank())
                    .distinct()
                    .toList();
            if (pushTokens.isEmpty()) {
                // A device can re-register after app startup. Keep the outbox row
                // retryable instead of falsely marking a notification as SENT.
                throw PushDeliveryException.retryable("NO_REGISTERED_PUSH_TOKEN", null);
            }
            if (payload.getKind() == NotificationPushKind.CHAT) {
                fcmService.sendChatPushNotificationToTokens(
                        pushTokens,
                        payload.getMessageId(),
                        payload.getConversationId(),
                        payload.getConversationName(),
                        payload.getSenderId(),
                        payload.getSenderName(),
                        payload.getSenderAvatarUrl(),
                        payload.getBody());
            } else {
                fcmService.sendPushNotificationToTokens(
                        pushTokens, payload.getTitle(), payload.getBody());
            }
            markSent(notification, LocalDateTime.now());
        } catch (PushDeliveryException failure) {
            handleFailure(notification, failure, LocalDateTime.now());
        } catch (RuntimeException unexpected) {
            handleFailure(notification,
                    PushDeliveryException.retryable("DELIVERY_INTERNAL", unexpected),
                    LocalDateTime.now());
        }
    }

    private void markSent(Notification notification, LocalDateTime now) {
        Query owned = ownedLease(notification);
        Update update = new Update()
                .set("deliveryStatus", NotificationDeliveryStatus.SENT)
                .set("deliveredAt", now)
                .unset("deliveryLeaseUntil")
                .unset("deliveryLeaseId")
                .unset("lastDeliveryErrorCode")
                .set("updatedAt", now);
        if (mongoTemplate.updateFirst(owned, update, Notification.class).getModifiedCount() == 1) {
            LocalDateTime createdAt = notification.getCreatedAt() == null ? now : notification.getCreatedAt();
            metrics.recordSent(Duration.between(
                    createdAt.atZone(ZoneId.systemDefault()).toInstant(),
                    now.atZone(ZoneId.systemDefault()).toInstant()));
        }
    }

    private void handleFailure(Notification notification, PushDeliveryException failure, LocalDateTime now) {
        boolean deadLetter = !failure.isRetryable() || notification.getDeliveryAttempts() >= maxAttempts;
        Update update = new Update()
                .set("lastDeliveryErrorCode", sanitizeErrorCode(failure.getErrorCode()))
                .unset("deliveryLeaseUntil")
                .unset("deliveryLeaseId")
                .set("updatedAt", now);
        if (deadLetter) {
            update.set("deliveryStatus", NotificationDeliveryStatus.FAILED)
                    .set("deadLetteredAt", now);
        } else {
            update.set("deliveryStatus", NotificationDeliveryStatus.PENDING)
                    .set("nextDeliveryAttemptAt", now.plus(computeBackoff(notification.getDeliveryAttempts())));
        }
        long modified = mongoTemplate.updateFirst(ownedLease(notification), update, Notification.class).getModifiedCount();
        if (modified == 1) {
            if (deadLetter) {
                metrics.recordDeadLetter();
                log.warn("Notification outbox delivery moved to dead-letter; errorCode={}",
                        sanitizeErrorCode(failure.getErrorCode()));
            } else {
                metrics.recordRetry();
                log.info("Notification outbox delivery scheduled for retry; errorCode={}",
                        sanitizeErrorCode(failure.getErrorCode()));
            }
        }
    }

    private Query ownedLease(Notification notification) {
        return Query.query(Criteria.where("_id").is(notification.getId())
                .and("deliveryStatus").is(NotificationDeliveryStatus.PROCESSING)
                .and("deliveryLeaseId").is(notification.getDeliveryLeaseId()));
    }

    Duration computeBackoff(int attempt) {
        int exponent = Math.max(0, Math.min(attempt - 1, 30));
        long baseMillis = baseBackoff.toMillis();
        long cappedMillis;
        try {
            cappedMillis = Math.min(Math.multiplyExact(baseMillis, 1L << exponent), maxBackoff.toMillis());
        } catch (ArithmeticException overflow) {
            cappedMillis = maxBackoff.toMillis();
        }
        double jitter = ThreadLocalRandom.current().nextDouble(-jitterRatio, jitterRatio);
        return Duration.ofMillis(Math.max(1L, Math.round(cappedMillis * (1.0 + jitter))));
    }

    private static String sanitizeErrorCode(String errorCode) {
        if (errorCode == null || !errorCode.matches("[A-Z0-9_]{1,64}")) {
            return "DELIVERY_ERROR";
        }
        return errorCode;
    }
}
