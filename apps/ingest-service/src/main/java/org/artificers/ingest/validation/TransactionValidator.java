package org.artificers.ingest.validation;

import org.artificers.ingest.model.TransactionRecord;

/** Validates {@link TransactionRecord} instances. */
public interface TransactionValidator {
    void validate(TransactionRecord tx);
}
