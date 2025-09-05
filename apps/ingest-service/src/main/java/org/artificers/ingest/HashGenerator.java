package org.artificers.ingest;

import java.time.Instant;
import org.apache.commons.codec.digest.DigestUtils;

final class HashGenerator {
    private HashGenerator() {}

    static String sha256(String accountId, Money amount, Instant occurredAt, String merchant) {
        String canonical = String.join(
                "|",
                normalize(accountId),
                normalize(amount == null ? null : Long.toString(amount.cents())),
                normalize(amount == null ? null : amount.currency()),
                normalize(occurredAt == null ? null : occurredAt.toString()),
                normalize(merchant));
        return DigestUtils.sha256Hex(canonical);
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
