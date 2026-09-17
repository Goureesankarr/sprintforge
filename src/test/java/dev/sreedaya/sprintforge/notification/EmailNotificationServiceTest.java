package dev.sreedaya.sprintforge.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    @Mock
    NotificationOutboxRepository outbox;

    @Test
    void writesInvitationToOutboxWithStableIdempotencyKey() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        EmailNotificationService service = new EmailNotificationService(outbox, clock, 5);
        UUID projectId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        service.projectInvitation("member@example.org", "Payments", projectId, memberId);

        ArgumentCaptor<NotificationOutbox> event =
                ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outbox).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("PROJECT_INVITATION");
        assertThat(event.getValue().getAggregateId()).isEqualTo(projectId);
        assertThat(event.getValue().getRecipient()).isEqualTo("member@example.org");
        assertThat(event.getValue().getStatus())
                .isEqualTo(NotificationOutbox.Status.PENDING);
        assertThat(event.getValue().getNextAttemptAt()).isEqualTo(NOW);
        assertThat(event.getValue().getMaxAttempts()).isEqualTo(5);
        assertThat(event.getValue().getIdempotencyKey())
                .isEqualTo("PROJECT_INVITATION:" + projectId + ":" + memberId);
    }

    @Test
    void doesNotEnqueueAnAlreadyRecordedInvitation() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        EmailNotificationService service = new EmailNotificationService(outbox, clock, 5);
        UUID projectId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(outbox.existsByIdempotencyKey(
                        "PROJECT_INVITATION:" + projectId + ":" + memberId))
                .thenReturn(true);

        service.projectInvitation("member@example.org", "Payments", projectId, memberId);

        verify(outbox, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
