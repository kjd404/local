package org.artificers.ingest;

final class TransactionValidator {
    private TransactionValidator() {}

    static void validate(TransactionRecord tx) {
        if (tx.accountId() == null || tx.accountId().isBlank()) {
            throw new IllegalArgumentException("accountId is required");
        }
        if (tx.amount() == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (tx.amount().currency() == null || tx.amount().currency().length() != 3) {
            throw new IllegalArgumentException("currency must be 3-letter code");
        }
        if (tx.hash() == null || tx.hash().isBlank()) {
            throw new IllegalArgumentException("hash is required");
        }
    }
}
