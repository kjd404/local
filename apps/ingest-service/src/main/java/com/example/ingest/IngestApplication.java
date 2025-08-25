package com.example.ingest;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;

import java.nio.file.Paths;

@SpringBootApplication
public class IngestApplication {
    public static void main(String[] args) {
        String rawUrl = System.getenv("DB_URL");
        if (rawUrl != null) {
            System.setProperty("spring.datasource.url", JdbcUrl.from(rawUrl));
        }
        SpringApplication.run(IngestApplication.class, args);
    }

    @Bean
    CommandLineRunner runner(IngestService service, ApplicationArguments args) {
        return a -> {
            if (args.containsOption("mode") && args.getOptionValues("mode").contains("scan")) {
                String input = args.containsOption("input") ? args.getOptionValues("input").get(0) : "/incoming";
                service.scanAndIngest(Paths.get(input));
                System.exit(0);
            }
        };
    }
}
