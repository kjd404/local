package com.example.teller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Configuration
@Profile("!test")
public final class TellerClientConfig {

    private static final Logger log = LoggerFactory.getLogger(TellerClientConfig.class);

    @Bean
    public TellerClient tellerClient() {
        String tokensEnv = System.getenv("TELLER_TOKENS");
        String certFile = System.getenv("TELLER_CERT_FILE");
        String keyFile = System.getenv("TELLER_KEY_FILE");
        log.info("Starting with TELLER_TOKENS={} TELLER_CERT_FILE={} TELLER_KEY_FILE={}", tokensEnv, certFile, keyFile);

        List<String> tokens = (tokensEnv == null || tokensEnv.isBlank()) ? List.of() :
            Arrays.stream(tokensEnv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        Path certPath = Paths.get(certFile);
        Path keyPath = Paths.get(keyFile);

        try {
            TellerClientFactory factory = new TellerClientFactory(certPath, keyPath);
            return factory.create(tokens);
        } catch (Exception e) {
            log.error("Failed to create TellerClient tokens={} certFile={} keyFile={}", tokens, certPath, keyPath, e);
            throw e;
        }
    }
}
