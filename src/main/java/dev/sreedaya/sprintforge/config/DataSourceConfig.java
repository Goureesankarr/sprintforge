package dev.sreedaya.sprintforge.config;

import java.net.URI;
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
            URI uri = URI.create(url);
            StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                    .append(uri.getHost());
            if (uri.getPort() != -1) {
                jdbcUrl.append(':').append(uri.getPort());
            }
            jdbcUrl.append(uri.getRawPath());
            if (uri.getRawQuery() != null) {
                jdbcUrl.append('?').append(uri.getRawQuery());
            }
            return jdbcUrl.toString();
        }
        return url;
    }
}
