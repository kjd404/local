package org.artificers.ingest;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the application's {@link DataSource} and logs the connection URL
 * with sensitive information removed. If the initial connection attempt
 * fails, the error is logged and the application continues to start so that it
 * can fail gracefully when the DataSource is first used.
 */
@Configuration
public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    DataSource dataSource(DataSourceProperties properties) {
        String url = properties.getUrl();
        String safeUrl = sanitize(url);
        String user = properties.getUsername();
        if (url != null) {
            log.info("Attempting database connection to {} as user {}", safeUrl, user);
        } else {
            log.warn("No database URL configured");
        }
        HikariDataSource ds = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
        // Try a connection to surface any configuration issues early but don't
        // prevent the app from starting if it fails.
        try (var ignored = ds.getConnection()) {
            log.info("Database connection established");
        } catch (Exception ex) {
            log.error("Database connection failed for url={} user={}", safeUrl, user, ex);
        }
        return ds;
    }

    /**
     * Remove credentials from a JDBC URL so it can be safely logged.
     */
    static String sanitize(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("(?<=//)[^/@]+:[^@]+@", "");
    }
}
