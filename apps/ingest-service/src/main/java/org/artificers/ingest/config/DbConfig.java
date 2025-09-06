package org.artificers.ingest.config;

/** Immutable database configuration. */
public record DbConfig(String url, String user, String password) {}
