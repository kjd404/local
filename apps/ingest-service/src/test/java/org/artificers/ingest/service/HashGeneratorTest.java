package org.artificers.ingest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.artificers.ingest.model.Money;
import org.artificers.ingest.service.HashGenerator;

class HashGeneratorTest {

    @Test
    void identicalInputsProduceSameHash() {
        Money amount = new Money(123L, "USD");
        Instant occurred = Instant.parse("2024-01-01T00:00:00Z");

        String hash1 = HashGenerator.sha256("acct", amount, occurred, "merchant");
        String hash2 = HashGenerator.sha256("acct", amount, occurred, "merchant");

        assertEquals(hash1, hash2);

        String nullHash1 = HashGenerator.sha256(null, null, null, null);
        String nullHash2 = HashGenerator.sha256(null, null, null, null);

        assertEquals(nullHash1, nullHash2);
    }
}
