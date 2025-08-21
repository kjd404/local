package com.example.teller;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.http.HttpRequest;

/**
 * Executes HTTP requests and returns parsed JSON responses.
 */
public interface RequestExecutor {

    JsonNode execute(HttpRequest request) throws IOException, InterruptedException;
}

