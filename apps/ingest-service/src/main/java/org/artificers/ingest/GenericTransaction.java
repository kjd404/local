package org.artificers.ingest;

import java.time.Instant;

public record GenericTransaction(
        String accountId,
        Instant occurredAt,
        Instant postedAt,
        Money amount,
        String merchant,
        String category,
        String type,
        String memo,
        String hash,
        String rawJson
) implements TransactionRecord {}
