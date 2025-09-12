package org.artificers.ingest.app;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UrlSanitizerTest {
  @Test
  void sanitizeRemovesCredentials() {
    String url = "jdbc:postgresql://user:secret@host:5432/db";
    String sanitized = IngestApp.sanitize(url);
    assertEquals("jdbc:postgresql://host:5432/db", sanitized);
  }
}
