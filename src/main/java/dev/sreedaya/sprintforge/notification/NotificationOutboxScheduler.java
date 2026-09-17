package dev.sreedaya.sprintforge.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.outbox.worker.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationOutboxScheduler {
    private final NotificationOutboxProcessor processor;

    public NotificationOutboxScheduler(NotificationOutboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(
            initialDelayString = "${app.outbox.initial-delay:PT10S}",
            fixedDelayString = "${app.outbox.poll-delay:PT5S}")
    public void drainBatch() {
        processor.processBatch();
    }
}
