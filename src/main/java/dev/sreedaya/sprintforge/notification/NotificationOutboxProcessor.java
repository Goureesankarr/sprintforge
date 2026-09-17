package dev.sreedaya.sprintforge.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationOutboxProcessor {
    private static final Logger log =
            LoggerFactory.getLogger(NotificationOutboxProcessor.class);

    private final NotificationOutboxRepository outbox;
    private final EmailNotificationSender sender;
    private final Clock clock;
    private final int batchSize;
    private final Duration retryBase;
    private final Duration retryMaximum;
    private final Counter processed;
    private final Counter retried;
    private final Counter deadLettered;

    public NotificationOutboxProcessor(
            NotificationOutboxRepository outbox,
            EmailNotificationSender sender,
            Clock clock,
            MeterRegistry meterRegistry,
            @Value("${app.outbox.batch-size:20}") int batchSize,
            @Value("${app.outbox.retry-base:PT10S}") Duration retryBase,
            @Value("${app.outbox.retry-maximum:PT15M}") Duration retryMaximum) {
        this.outbox = outbox;
        this.sender = sender;
        this.clock = clock;
        this.batchSize = batchSize;
        this.retryBase = retryBase;
        this.retryMaximum = retryMaximum;
        this.processed = meterRegistry.counter("sprintforge.outbox.processed");
        this.retried = meterRegistry.counter("sprintforge.outbox.retried");
        this.deadLettered = meterRegistry.counter("sprintforge.outbox.dead.lettered");
    }

    @Transactional
    public int processBatch() {
        Instant now = clock.instant();
        List<NotificationOutbox> events = outbox.lockDue(now, batchSize);
        for (NotificationOutbox event : events) {
            process(event, now);
        }
        return events.size();
    }

    private void process(NotificationOutbox event, Instant now) {
        event.setStatus(NotificationOutbox.Status.PROCESSING);
        int attempt = event.getAttempts() + 1;
        event.setAttempts(attempt);
        try {
            sender.send(event);
            event.setStatus(NotificationOutbox.Status.PROCESSED);
            event.setProcessedAt(now);
            event.setLastError(null);
            processed.increment();
            log.info("Outbox event delivered: eventId={}, type={}, attempt={}",
                    event.getId(), event.getEventType(), attempt);
        } catch (RuntimeException exception) {
            event.setLastError(safeMessage(exception));
            if (attempt >= event.getMaxAttempts()) {
                event.setStatus(NotificationOutbox.Status.DEAD);
                event.setDeadLetteredAt(now);
                deadLettered.increment();
                log.error("Outbox event dead-lettered: eventId={}, type={}, attempts={}",
                        event.getId(), event.getEventType(), attempt, exception);
            } else {
                event.setStatus(NotificationOutbox.Status.RETRY);
                event.setNextAttemptAt(now.plus(backoff(attempt)));
                retried.increment();
                log.warn("Outbox delivery scheduled for retry: eventId={}, type={}, attempt={}",
                        event.getId(), event.getEventType(), attempt, exception);
            }
        }
    }

    private Duration backoff(int attempt) {
        int exponent = Math.min(Math.max(attempt - 1, 0), 20);
        Duration candidate = retryBase.multipliedBy(1L << exponent);
        return candidate.compareTo(retryMaximum) > 0 ? retryMaximum : candidate;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 1000));
    }
}
