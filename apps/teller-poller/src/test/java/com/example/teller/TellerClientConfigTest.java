package com.example.teller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TellerClientConfigTest {
    @Test
    void missingTokensThrows() {
        TellerClientConfig cfg = new TellerClientConfig(null, "/tmp/cert.pem", "/tmp/key.pem");
        assertThrows(IllegalStateException.class, cfg::tellerClient);
    }

    @Test
    void missingCertThrows() {
        TellerClientConfig cfg = new TellerClientConfig("tok", null, "/tmp/key.pem");
        assertThrows(IllegalStateException.class, cfg::tellerClient);
    }

    @Test
    void missingKeyThrows() {
        TellerClientConfig cfg = new TellerClientConfig("tok", "/tmp/cert.pem", "");
        assertThrows(IllegalStateException.class, cfg::tellerClient);
    }
}
