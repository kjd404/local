package org.artificers.ingest.config;

import java.nio.file.Path;

/** Immutable application paths configuration. */
public record IngestConfig(Path ingestDir, Path configDir) {}
