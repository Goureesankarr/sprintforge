package dev.sreedaya.sprintforge.config;

import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    DataSource dataSource(DataSourceProperties properties) {
        properties.setUrl(normalizePostgresUrl(properties.getUrl()));
        return properties.initializeDataSourceBuilder().build();
    }

    static String normalizePostgresUrl(String url) {
        if (url == null || url.startsWith("jdbc:")) {
            return url;
        }
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            return "jdbc:" + url;
        }
        return url;
    }
}
