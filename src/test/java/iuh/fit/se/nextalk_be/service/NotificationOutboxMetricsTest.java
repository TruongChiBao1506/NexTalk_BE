package iuh.fit.se.nextalk_be.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import iuh.fit.se.nextalk_be.entity.NotificationDeliveryStatus;
import iuh.fit.se.nextalk_be.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationOutboxMetricsTest {
    @Test
    void exposesQueueDepthLatencyAndErrorRatioWithoutPayloadTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationRepository repository = mock(NotificationRepository.class);
        when(repository.countByDeliveryStatus(NotificationDeliveryStatus.PENDING)).thenReturn(7L);
        when(repository.countByDeliveryStatus(NotificationDeliveryStatus.PROCESSING)).thenReturn(2L);
        when(repository.countByDeliveryStatus(NotificationDeliveryStatus.FAILED)).thenReturn(1L);
        NotificationOutboxMetrics metrics = new NotificationOutboxMetrics(registry, repository);

        metrics.refreshQueueDepth();
        metrics.recordAttempt();
        metrics.recordRetry();
        metrics.recordAttempt();
        metrics.recordSent(Duration.ofSeconds(3));

        assertThat(registry.get("nextalk.notification.outbox.queue.depth")
                .tag("status", "pending").gauge().value()).isEqualTo(7.0);
        assertThat(registry.get("nextalk.notification.outbox.queue.depth")
                .tag("status", "processing").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("nextalk.notification.outbox.queue.depth")
                .tag("status", "failed").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("nextalk.notification.outbox.error.ratio").gauge().value())
                .isEqualTo(0.5);
        assertThat(registry.get("nextalk.notification.outbox.delivery.latency").timer().count())
                .isEqualTo(1);
    }
}
