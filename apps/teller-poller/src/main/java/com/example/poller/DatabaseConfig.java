package com.example.poller;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the application's {@link DataSource} and logs the connection URL
 * and user. If the initial connection attempt fails, the error is logged so
 * that misconfiguration is obvious in the logs.
 */
@Configuration
public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    DataSource dataSource(DataSourceProperties properties) {
        String url = properties.getUrl();
        String safeUrl = JdbcUrl.sanitize(url);
        String user = properties.getUsername();
        if (url != null) {
            log.info("Attempting database connection to {} as user {}", safeUrl, user);
        } else {
            log.warn("No database URL configured");
        }
        HikariDataSource ds = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
        try (var ignored = ds.getConnection()) {
            log.info("Database connection established");
        } catch (Exception ex) {
            log.error("Database connection failed for url={} user={}", safeUrl, user, ex);
        }
        return ds;
    }
}

