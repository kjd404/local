package com.example.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class IngestApplication {
    private static final Logger log = LoggerFactory.getLogger(IngestApplication.class);

    public static void main(String[] args) {
        String rawUrl = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        log.info("Starting with DB_URL={} DB_USER={}", DatabaseConfig.sanitize(rawUrl), user);
        if (rawUrl != null) {
            System.setProperty("spring.datasource.url", JdbcUrl.from(rawUrl));
        }
        SpringApplication.run(IngestApplication.class, args);
    }

    @Bean
    CommandLineRunner runner(IngestService service, ApplicationArguments args) {
        return a -> {
            if (processArgs(service, args)) {
                System.exit(0);
            }
        };
    }

    boolean processArgs(IngestService service, ApplicationArguments args) throws Exception {
        if (args.containsOption("file")) {
            String file = args.getOptionValues("file").get(0);
            Path p = Path.of(file);
            String shorthand = AccountResolver.extractShorthand(p);
            boolean ok = shorthand != null && service.ingestFile(p, shorthand);
            if (!ok) {
                log.warn("Ingestion failed for {}", file);
            }
            return true;
        }
        if (args.containsOption("mode") && args.getOptionValues("mode").contains("scan")) {
            String input = args.containsOption("input") ? args.getOptionValues("input").get(0) : "/incoming";
            service.scanAndIngest(Paths.get(input));
            return true;
        }
        return false;
    }
}
