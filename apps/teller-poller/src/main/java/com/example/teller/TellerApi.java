package com.example.teller;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Operations exposed by the Teller API.
 */
public interface TellerApi {

    JsonNode listAccounts(String token) throws IOException, InterruptedException;

    JsonNode listTransactions(String token, String accountId, String cursor)
        throws IOException, InterruptedException;
}

