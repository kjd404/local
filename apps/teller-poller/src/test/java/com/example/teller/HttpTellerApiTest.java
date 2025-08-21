package com.example.teller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HttpTellerApiTest {

    @Test
    void listAccountsDelegatesToExecutor() throws Exception {
        RequestExecutor executor = mock(RequestExecutor.class);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode sample = mapper.createObjectNode();
        when(executor.execute(any())).thenReturn(sample);

        HttpTellerApi api = new HttpTellerApi(executor);
        JsonNode result = api.listAccounts("token123");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(executor).execute(captor.capture());
        HttpRequest request = captor.getValue();

        assertEquals("https://api.teller.io/accounts", request.uri().toString());
        assertEquals("Bearer token123", request.headers().firstValue("Authorization").orElse(""));
        assertSame(sample, result);
    }

    @Test
    void listTransactionsAddsCursorParameter() throws Exception {
        RequestExecutor executor = mock(RequestExecutor.class);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode sample = mapper.createObjectNode();
        when(executor.execute(any())).thenReturn(sample);

        HttpTellerApi api = new HttpTellerApi(executor);
        JsonNode result = api.listTransactions("tok", "acc1", "cur#1");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(executor).execute(captor.capture());
        HttpRequest request = captor.getValue();

        assertEquals("https://api.teller.io/accounts/acc1/transactions?cursor=cur%231", request.uri().toString());
        assertEquals("Bearer tok", request.headers().firstValue("Authorization").orElse(""));
        assertSame(sample, result);
    }
}
