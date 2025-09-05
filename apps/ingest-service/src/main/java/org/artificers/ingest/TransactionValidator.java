package org.artificers.ingest;

/** Validates {@link TransactionRecord} instances. */
public interface TransactionValidator {
    void validate(TransactionRecord tx);
}

