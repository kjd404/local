package org.artificers.ingest;

import java.time.Instant;

/**
 * Immutable account record.
 */
public record Account(
        long id,
        String institution,
        String externalId,
        String displayName,
        Instant createdAt,
        Instant updatedAt
) {}
