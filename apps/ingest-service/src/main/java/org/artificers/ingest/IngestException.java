package org.artificers.ingest;

/** General checked exception for ingestion failures. */
public class IngestException extends Exception {
    public IngestException(String message) {
        super(message);
    }

    public IngestException(String message, Throwable cause) {
        super(message, cause);
    }
}
