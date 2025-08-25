package com.example.teller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.util.List;

@Configuration
@Profile("test")
class DummyTellerConfig {
    @Bean
    public TellerClient tellerClient() {
        TellerApi api = new TellerApi() {
            private final ObjectMapper mapper = new ObjectMapper();
            @Override
            public JsonNode listAccounts(String token) throws IOException, InterruptedException {
                return mapper.createArrayNode();
            }
            @Override
            public JsonNode listTransactions(String token, String accountId, String cursor) throws IOException, InterruptedException {
                return mapper.createArrayNode();
            }
        };
        return new TellerClient(List.of("test"), api);
    }
}
