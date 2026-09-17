package dev.sreedaya.sprintforge.notification;

import dev.sreedaya.sprintforge.common.ApiExceptionHandler.ConflictException;
import dev.sreedaya.sprintforge.common.ApiExceptionHandler.NotFoundException;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/outbox")
@PreAuthorize("hasRole('ADMIN')")
public class NotificationOutboxAdminController {
    private final NotificationOutboxRepository outbox;
    private final Clock clock;

    public NotificationOutboxAdminController(
            NotificationOutboxRepository outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    public record OutboxView(
            UUID id,
            String eventType,
            UUID aggregateId,
            String recipient,
            String status,
            int attempts,
            int maxAttempts,
            Instant nextAttemptAt,
            Instant createdAt,
            Instant processedAt,
            Instant deadLetteredAt,
            String lastError) {
        static OutboxView from(NotificationOutbox event) {
            return new OutboxView(
                    event.getId(),
                    event.getEventType(),
                    event.getAggregateId(),
                    event.getRecipient(),
                    event.getStatus().name(),
                    event.getAttempts(),
                    event.getMaxAttempts(),
                    event.getNextAttemptAt(),
                    event.getCreatedAt(),
                    event.getProcessedAt(),
                    event.getDeadLetteredAt(),
                    event.getLastError());
        }
    }

    @GetMapping
    Page<OutboxView> list(
            @RequestParam(required = false) NotificationOutbox.Status status,
            Pageable pageable) {
        Page<NotificationOutbox> events = status == null
                ? outbox.findAll(pageable)
                : outbox.findByStatus(status, pageable);
        return events.map(OutboxView::from);
    }

    @PostMapping("/{eventId}/replay")
    @Transactional
    OutboxView replay(@PathVariable UUID eventId) {
        NotificationOutbox event = outbox.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Outbox event not found"));
        if (event.getStatus() != NotificationOutbox.Status.DEAD) {
            throw new ConflictException("Only dead-lettered events can be replayed");
        }
        event.setStatus(NotificationOutbox.Status.RETRY);
        event.setAttempts(0);
        event.setNextAttemptAt(clock.instant());
        event.setProcessedAt(null);
        event.setDeadLetteredAt(null);
        event.setLastError(null);
        return OutboxView.from(event);
    }
}
