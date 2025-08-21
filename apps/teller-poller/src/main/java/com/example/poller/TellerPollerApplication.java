package com.example.poller;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;

import java.nio.file.Paths;

@SpringBootApplication
public class TellerPollerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TellerPollerApplication.class, args);
    }

    @Bean
    CommandLineRunner runner(TellerPollerService service, ApplicationArguments args) {
        return a -> {
            if (args.containsOption("mode") && args.getOptionValues("mode").contains("scan")) {
                String input = args.containsOption("input") ? args.getOptionValues("input").get(0) : "/incoming";
                service.scanAndIngest(Paths.get(input));
                System.exit(0);
            }
        };
    }
}
