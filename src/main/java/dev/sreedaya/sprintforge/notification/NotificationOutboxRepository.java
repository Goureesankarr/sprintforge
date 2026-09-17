package dev.sreedaya.sprintforge.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository
        extends JpaRepository<NotificationOutbox, UUID> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    long countByStatusIn(List<NotificationOutbox.Status> statuses);

    Page<NotificationOutbox> findByStatus(
            NotificationOutbox.Status status, Pageable pageable);

    @Query(value = """
            SELECT *
            FROM notification_outbox
            WHERE status IN ('PENDING', 'RETRY')
              AND next_attempt_at <= :now
            ORDER BY next_attempt_at, created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<NotificationOutbox> lockDue(
            @Param("now") Instant now, @Param("batchSize") int batchSize);
}
