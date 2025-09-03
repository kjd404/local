package org.artificers.ingest;

import java.time.Instant;

public interface TransactionRecord {
    String accountId();
    Instant occurredAt();
    Instant postedAt();
    long amountCents();
    String currency();
    String merchant();
    String category();
    String type();
    String memo();
    String hash();
    String rawJson();
}
