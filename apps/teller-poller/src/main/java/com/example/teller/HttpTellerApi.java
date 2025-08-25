package com.example.teller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

/**
 * HTTP implementation of {@link TellerApi}.
 */
public final class HttpTellerApi implements TellerApi {

    private static final Logger log = LoggerFactory.getLogger(HttpTellerApi.class);

    private final RequestExecutor executor;
    private final URI baseUri = URI.create("https://api.teller.io");

    public HttpTellerApi(RequestExecutor executor) {
        this.executor = executor;
    }

    @Override
    public JsonNode listAccounts(String token) throws IOException, InterruptedException {
        URI uri = baseUri.resolve("/accounts");
        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        log.debug("GET {} tokenPrefix={}", uri, mask(token));
        return executor.execute(request);
    }

    @Override
    public JsonNode listTransactions(String token, String accountId, String cursor)
        throws IOException, InterruptedException {
        String path = "/accounts/" + accountId + "/transactions";
        if (cursor != null && !cursor.isBlank()) {
            path += "?cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }
        URI uri = baseUri.resolve(path);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        log.debug("GET {} tokenPrefix={}", uri, mask(token));
        return executor.execute(request);
    }

    private String mask(String token) {
        if (token == null) {
            return "null";
        }
        int prefix = Math.min(6, token.length());
        return token.substring(0, prefix) + "...";
    }
}

