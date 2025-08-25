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

    private final String tokensEnv;
    private final String certFile;
    private final String keyFile;

    public TellerClientConfig() {
        this(System.getenv("TELLER_TOKENS"), System.getenv("TELLER_CERT_FILE"), System.getenv("TELLER_KEY_FILE"));
    }

    // package-private for tests
    TellerClientConfig(String tokensEnv, String certFile, String keyFile) {
        this.tokensEnv = tokensEnv;
        this.certFile = certFile;
        this.keyFile = keyFile;
    }

    @Bean
    public TellerClient tellerClient() {
        if (tokensEnv == null || tokensEnv.isBlank()) {
            log.error("TELLER_TOKENS is required but was '{}'", tokensEnv);
            throw new IllegalStateException("TELLER_TOKENS is required");
        }
        if (certFile == null || certFile.isBlank()) {
            log.error("TELLER_CERT_FILE is required but was '{}'", certFile);
            throw new IllegalStateException("TELLER_CERT_FILE is required");
        }
        if (keyFile == null || keyFile.isBlank()) {
            log.error("TELLER_KEY_FILE is required but was '{}'", keyFile);
            throw new IllegalStateException("TELLER_KEY_FILE is required");
        }

        List<String> tokens = Arrays.stream(tokensEnv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (tokens.isEmpty()) {
            log.error("No valid TELLER_TOKENS provided: {}", tokensEnv);
            throw new IllegalStateException("TELLER_TOKENS is required");
        }

        Path certPath = Paths.get(certFile);
        Path keyPath = Paths.get(keyFile);
        log.info("Starting with {} token(s) TELLER_CERT_FILE={} TELLER_KEY_FILE={}",
                tokens.size(), certPath, keyPath);

        try {
            TellerClientFactory factory = new TellerClientFactory(certPath, keyPath);
            return factory.create(tokens);
        } catch (Exception e) {
            log.error("Failed to create TellerClient tokens={} certFile={} keyFile={}", tokens, certPath, keyPath, e);
            throw e;
        }
    }
}
