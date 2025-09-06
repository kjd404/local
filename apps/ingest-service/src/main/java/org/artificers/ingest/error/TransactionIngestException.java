package org.artificers.ingest.error;

import org.artificers.ingest.model.TransactionRecord;

public class TransactionIngestException extends RuntimeException {
    private final TransactionRecord record;

    public TransactionIngestException(TransactionRecord record, Throwable cause) {
        super("Failed to ingest transaction " + record, cause);
        this.record = record;
    }

    public TransactionRecord record() {
        return record;
    }
}
