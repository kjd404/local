package com.example.teller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * High-level client for interacting with the Teller API.
 */
@Component
public final class TellerClient {

    private final List<String> tokens;
    private final TellerApi api;

    public TellerClient(List<String> tokens, TellerApi api) {
        this.tokens = List.copyOf(tokens);
        this.api = api;
    }

    public JsonNode listAccounts(String token) throws IOException, InterruptedException {
        return api.listAccounts(token);
    }

    public JsonNode listTransactions(String token, String accountId, String cursor)
        throws IOException, InterruptedException {
        return api.listTransactions(token, accountId, cursor);
    }

    public List<String> getTokens() {
        return tokens;
    }
}

