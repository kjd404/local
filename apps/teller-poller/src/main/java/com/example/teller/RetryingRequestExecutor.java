package com.example.teller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Sends HTTP requests with simple retry/backoff behaviour and parses JSON responses.
 */
public final class RetryingRequestExecutor implements RequestExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryingRequestExecutor.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final int maxAttempts;

    public RetryingRequestExecutor(HttpClient httpClient, ObjectMapper mapper) {
        this(httpClient, mapper, 3);
    }

    public RetryingRequestExecutor(HttpClient httpClient, ObjectMapper mapper, int maxAttempts) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public JsonNode execute(HttpRequest request) throws IOException, InterruptedException {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return mapper.readTree(response.body());
                }
                log.warn("HTTP {} {} failed status={} body={} auth={}",
                        request.method(), request.uri(), response.statusCode(), response.body(), maskAuth(request));
                throw new IOException("API error: " + response.statusCode() + " " + response.body());
            } catch (IOException | InterruptedException e) {
                if (attempts >= maxAttempts) {
                    throw e;
                }
                long backoffMillis = (long) Math.pow(2, attempts - 1) * 1000L;
                Thread.sleep(backoffMillis);
            }
        }
    }

    private String maskAuth(HttpRequest request) {
        return request.headers().firstValue("Authorization")
            .map(v -> {
                if (v.startsWith("Bearer ")) {
                    String token = v.substring(7);
                    int prefix = Math.min(6, token.length());
                    return "Bearer " + token.substring(0, prefix) + "...";
                }
                return v;
            }).orElse("none");
    }
}

