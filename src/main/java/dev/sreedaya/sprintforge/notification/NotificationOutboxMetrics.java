package dev.sreedaya.sprintforge.notification;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationOutboxMetrics {
    public NotificationOutboxMetrics(
            NotificationOutboxRepository outbox, MeterRegistry meterRegistry) {
        Gauge.builder(
                        "sprintforge.outbox.pending",
                        outbox,
                        repository -> repository.countByStatusIn(List.of(
                                NotificationOutbox.Status.PENDING,
                                NotificationOutbox.Status.RETRY)))
                .description("Notification outbox events awaiting delivery")
                .register(meterRegistry);
        Gauge.builder(
                        "sprintforge.outbox.dead",
                        outbox,
                        repository -> repository.countByStatusIn(
                                List.of(NotificationOutbox.Status.DEAD)))
                .description("Notification outbox events in the dead-letter state")
                .register(meterRegistry);
    }
}
