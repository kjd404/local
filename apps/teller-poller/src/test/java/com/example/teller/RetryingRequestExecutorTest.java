package com.example.teller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetryingRequestExecutorTest {

    @Test
    void retriesOnFailureAndSucceeds() throws Exception {
        HttpClient client = mock(HttpClient.class);
        ObjectMapper mapper = new ObjectMapper();

        HttpResponse<String> first = mock(HttpResponse.class);
        when(first.statusCode()).thenReturn(500);
        when(first.body()).thenReturn("oops");
        HttpResponse<String> second = mock(HttpResponse.class);
        when(second.statusCode()).thenReturn(200);
        when(second.body()).thenReturn("{\"ok\":true}");

        when(client.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(first, second);

        RetryingRequestExecutor executor = new RetryingRequestExecutor(client, mapper, 3);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://test"))
            .GET().build();
        JsonNode result = executor.execute(request);

        verify(client, times(2)).send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertTrue(result.get("ok").asBoolean());
    }

    @Test
    void throwsAfterMaxAttempts() throws Exception {
        HttpClient client = mock(HttpClient.class);
        ObjectMapper mapper = new ObjectMapper();
        when(client.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenThrow(new IOException("fail"));

        RetryingRequestExecutor executor = new RetryingRequestExecutor(client, mapper, 2);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://test"))
            .GET().build();

        assertThrows(IOException.class, () -> executor.execute(request));
        verify(client, times(2)).send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }
}
