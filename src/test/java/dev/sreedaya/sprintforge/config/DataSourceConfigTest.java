package dev.sreedaya.sprintforge.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DataSourceConfigTest {

    @Test
    void convertsPlatformPostgresUrlToJdbcUrl() {
        assertThat(DataSourceConfig.normalizePostgresUrl(
                        "postgresql://user:password@database:5432/sprintforge"))
                .isEqualTo("jdbc:postgresql://user:password@database:5432/sprintforge");
    }

    @Test
    void leavesJdbcUrlUnchanged() {
        assertThat(DataSourceConfig.normalizePostgresUrl(
                        "jdbc:postgresql://localhost:5432/sprintforge"))
                .isEqualTo("jdbc:postgresql://localhost:5432/sprintforge");
    }
}
