package com.example.teller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Sends HTTP requests with simple retry/backoff behaviour and parses JSON responses.
 */
public final class RetryingRequestExecutor implements RequestExecutor {

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
}

