package iuh.fit.se.nextalk_be.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import iuh.fit.se.nextalk_be.entity.NotificationDeliveryStatus;
import iuh.fit.se.nextalk_be.repository.NotificationRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class NotificationOutboxMetrics {
    private final NotificationRepository notificationRepository;
    private final AtomicLong pendingDepth = new AtomicLong();
    private final AtomicLong processingDepth = new AtomicLong();
    private final AtomicLong deadLetterDepth = new AtomicLong();
    private final AtomicLong totalAttempts = new AtomicLong();
    private final AtomicLong failedAttempts = new AtomicLong();
    private final Counter sentCounter;
    private final Counter retryCounter;
    private final Counter deadLetterCounter;
    private final Timer deliveryLatency;

    public NotificationOutboxMetrics(MeterRegistry registry, NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
        Gauge.builder("nextalk.notification.outbox.queue.depth", pendingDepth, AtomicLong::get)
                .tag("status", "pending").register(registry);
        Gauge.builder("nextalk.notification.outbox.queue.depth", processingDepth, AtomicLong::get)
                .tag("status", "processing").register(registry);
        Gauge.builder("nextalk.notification.outbox.queue.depth", deadLetterDepth, AtomicLong::get)
                .tag("status", "failed").register(registry);
        Gauge.builder("nextalk.notification.outbox.error.ratio", this, NotificationOutboxMetrics::errorRatio)
                .register(registry);
        sentCounter = Counter.builder("nextalk.notification.outbox.deliveries")
                .tag("outcome", "sent").register(registry);
        retryCounter = Counter.builder("nextalk.notification.outbox.deliveries")
                .tag("outcome", "retry").register(registry);
        deadLetterCounter = Counter.builder("nextalk.notification.outbox.deliveries")
                .tag("outcome", "dead_letter").register(registry);
        deliveryLatency = Timer.builder("nextalk.notification.outbox.delivery.latency")
                .publishPercentileHistogram().register(registry);
    }

    public void recordAttempt() {
        totalAttempts.incrementAndGet();
    }

    public void recordSent(Duration latency) {
        sentCounter.increment();
        deliveryLatency.record(latency.isNegative() ? Duration.ZERO : latency);
    }

    public void recordRetry() {
        failedAttempts.incrementAndGet();
        retryCounter.increment();
    }

    public void recordDeadLetter() {
        failedAttempts.incrementAndGet();
        deadLetterCounter.increment();
    }

    public void refreshQueueDepth() {
        pendingDepth.set(notificationRepository.countByDeliveryStatus(NotificationDeliveryStatus.PENDING));
        processingDepth.set(notificationRepository.countByDeliveryStatus(NotificationDeliveryStatus.PROCESSING));
        deadLetterDepth.set(notificationRepository.countByDeliveryStatus(NotificationDeliveryStatus.FAILED));
    }

    double errorRatio() {
        long attempts = totalAttempts.get();
        return attempts == 0 ? 0.0 : (double) failedAttempts.get() / attempts;
    }
}
