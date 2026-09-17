package dev.sreedaya.sprintforge.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailNotificationService {
    private final NotificationOutboxRepository outbox;
    private final Clock clock;
    private final int maxAttempts;

    public EmailNotificationService(
            NotificationOutboxRepository outbox,
            Clock clock,
            @Value("${app.outbox.max-attempts:5}") int maxAttempts) {
        this.outbox = outbox;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void projectInvitation(
            String recipient, String projectName, UUID projectId, UUID memberId) {
        String idempotencyKey = "PROJECT_INVITATION:" + projectId + ":" + memberId;
        if (outbox.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        Instant now = clock.instant();
        NotificationOutbox event = new NotificationOutbox();
        event.setId(UUID.randomUUID());
        event.setEventType("PROJECT_INVITATION");
        event.setAggregateType("PROJECT");
        event.setAggregateId(projectId);
        event.setIdempotencyKey(idempotencyKey);
        event.setRecipient(recipient);
        event.setSubject("You were added to " + projectName);
        event.setBody("You now have access to project " + projectName
                + " in SprintForge. Project ID: " + projectId);
        event.setStatus(NotificationOutbox.Status.PENDING);
        event.setAttempts(0);
        event.setMaxAttempts(maxAttempts);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        outbox.save(event);
    }
}
