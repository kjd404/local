package org.artificers.ingest;

import java.time.Instant;
import org.apache.commons.codec.digest.DigestUtils;

final class HashGenerator {
    private HashGenerator() {}

    static String sha256(String accountId, Money amount, Instant occurredAt, String merchant) {
        String occurred = occurredAt == null ? "" : occurredAt.toString();
        return DigestUtils.sha256Hex(accountId + amount.cents() + amount.currency() + occurred + merchant);
    }
}
