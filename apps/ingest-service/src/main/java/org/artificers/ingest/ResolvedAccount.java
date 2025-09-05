package org.artificers.ingest;

/**
 * Represents an account resolved from shorthand notation.
 */
public record ResolvedAccount(long id, String institution, String externalId) {}

