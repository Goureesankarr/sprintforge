package dev.sreedaya.sprintforge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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

        assertThat(migrations).isGreaterThanOrEqualTo(4);
        assertThat(refreshTokens).isEqualTo(1);
        assertThat(attachments).isEqualTo(1);
        assertThat(comments).isEqualTo(1);
    }
}
