package org.artificers.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Handles scanning directories and moving ingested files. */
public class FileIngestionService {
    private static final Logger log = LoggerFactory.getLogger(FileIngestionService.class);
    private final IngestService ingestService;

    public FileIngestionService(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    public void scanAndIngest(Path input) throws IOException {
        log.info("Scanning directory {}", input.toAbsolutePath());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(input, "*.csv")) {
            for (Path file : stream) {
                log.info("Found file {}", file);
                String shorthand = AccountResolver.extractShorthand(file);
                if (shorthand == null) {
                    log.warn("Skipping file {} with unrecognized name", file);
                    continue;
                }
                boolean ok = ingestService.ingestFile(file, shorthand);
                log.info("Ingestion {} for file {}", ok ? "succeeded" : "failed", file);
                Path targetDir = input.resolveSibling(ok ? "processed" : "error");
                Files.createDirectories(targetDir);
                Files.move(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
