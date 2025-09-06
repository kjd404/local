package org.artificers.ingest.model;

import java.time.Instant;

public interface TransactionRecord {
    String accountId();
    Instant occurredAt();
    Instant postedAt();
    Money amount();
    String merchant();
    String category();
    String type();
    String memo();
    String hash();
    String rawJson();
}
