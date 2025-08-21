package com.example.teller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TellerClientTest {

    @Test
    void tokensAreCopiedAndImmutable() {
        List<String> tokens = new ArrayList<>();
        tokens.add("t1");
        TellerApi api = mock(TellerApi.class);
        TellerClient client = new TellerClient(tokens, api);

        tokens.add("t2");
        assertEquals(List.of("t1"), client.getTokens());
        assertThrows(UnsupportedOperationException.class, () -> client.getTokens().add("x"));
    }

    @Test
    void delegatesToApi() throws Exception {
        TellerApi api = mock(TellerApi.class);
        TellerClient client = new TellerClient(List.of("tok"), api);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode sample = mapper.createObjectNode();

        when(api.listAccounts("tok")).thenReturn(sample);
        assertSame(sample, client.listAccounts("tok"));
        verify(api).listAccounts("tok");

        when(api.listTransactions("tok", "acc", null)).thenReturn(sample);
        assertSame(sample, client.listTransactions("tok", "acc", null));
        verify(api).listTransactions("tok", "acc", null);
    }
}
