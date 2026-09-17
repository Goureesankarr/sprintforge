package dev.sreedaya.sprintforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sreedaya.sprintforge.notification.NotificationOutbox;
import dev.sreedaya.sprintforge.notification.NotificationOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
    "spring.cache.type=simple"
})
class PostgresMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    NotificationOutboxRepository outbox;

    @Test
    void appliesProductionMigrationsToPostgres() {
        Integer migrations = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class);
        Integer refreshTokens = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'refresh_tokens'",
                Integer.class);
        Integer attachments = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'attachments'",
                Integer.class);
        Integer comments = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'work_item_comments'",
                Integer.class);
        Integer outbox = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'notification_outbox'",
                Integer.class);

        assertThat(migrations).isGreaterThanOrEqualTo(5);
        assertThat(refreshTokens).isEqualTo(1);
        assertThat(attachments).isEqualTo(1);
        assertThat(comments).isEqualTo(1);
        assertThat(outbox).isEqualTo(1);
    }

    @Test
    @Transactional
    void claimsDueEventsWithPostgresSkipLockedQuery() {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        NotificationOutbox event = new NotificationOutbox();
        event.setId(UUID.randomUUID());
        event.setEventType("PROJECT_INVITATION");
        event.setAggregateType("PROJECT");
        event.setAggregateId(UUID.randomUUID());
        event.setIdempotencyKey(UUID.randomUUID().toString());
        event.setRecipient("postgres-test@example.org");
        event.setSubject("Test invitation");
        event.setBody("Migration verification");
        event.setStatus(NotificationOutbox.Status.PENDING);
        event.setAttempts(0);
        event.setMaxAttempts(5);
        event.setNextAttemptAt(now.minusSeconds(1));
        event.setCreatedAt(now.minusSeconds(1));
        outbox.saveAndFlush(event);

        List<NotificationOutbox> claimed = outbox.lockDue(now, 10);

        assertThat(claimed).extracting(NotificationOutbox::getId).contains(event.getId());
    }
}
