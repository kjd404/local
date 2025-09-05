package org.artificers.ingest;

/** Immutable database configuration. */
public record DbConfig(String url, String user, String password) {}
