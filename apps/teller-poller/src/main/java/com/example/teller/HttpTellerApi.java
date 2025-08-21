package com.example.teller;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

/**
 * HTTP implementation of {@link TellerApi}.
 */
public final class HttpTellerApi implements TellerApi {

    private final RequestExecutor executor;
    private final URI baseUri = URI.create("https://api.teller.io");

    public HttpTellerApi(RequestExecutor executor) {
        this.executor = executor;
    }

    @Override
    public JsonNode listAccounts(String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/accounts"))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        return executor.execute(request);
    }

    @Override
    public JsonNode listTransactions(String token, String accountId, String cursor)
        throws IOException, InterruptedException {
        String path = "/accounts/" + accountId + "/transactions";
        if (cursor != null && !cursor.isBlank()) {
            path += "?cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        return executor.execute(request);
    }
}

