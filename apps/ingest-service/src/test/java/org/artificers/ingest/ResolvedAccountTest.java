package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.artificers.ingest.model.ResolvedAccount;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedAccountTest {

    @Test
    void equalityAndSerialization() throws Exception {
        ResolvedAccount first = new ResolvedAccount(1L, "bank", "ext");
        ResolvedAccount second = new ResolvedAccount(1L, "bank", "ext");

        assertThat(first).isEqualTo(second);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(first);
        assertThat(json).isEqualTo("{\"id\":1,\"institution\":\"bank\",\"externalId\":\"ext\"}");
        ResolvedAccount roundTrip = mapper.readValue(json, ResolvedAccount.class);
        assertThat(roundTrip).isEqualTo(first);
    }
}
