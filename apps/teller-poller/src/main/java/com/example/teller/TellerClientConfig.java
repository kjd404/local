package com.example.teller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Configuration
public final class TellerClientConfig {

    @Bean
    public TellerClient tellerClient() {
        String tokensEnv = System.getenv("TELLER_TOKENS");
        String certFile = System.getenv("TELLER_CERT_FILE");
        String keyFile = System.getenv("TELLER_KEY_FILE");

        List<String> tokens = (tokensEnv == null || tokensEnv.isBlank()) ? List.of() :
            Arrays.stream(tokensEnv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        Path certPath = Paths.get(certFile);
        Path keyPath = Paths.get(keyFile);

        TellerClientFactory factory = new TellerClientFactory(certPath, keyPath);
        return factory.create(tokens);
    }
}
