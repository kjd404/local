package org.artificers.ingest.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Handles scanning directories and moving ingested files. */
public class FileIngestionService {
    private static final Logger log = LoggerFactory.getLogger(FileIngestionService.class);
    private final IngestService ingestService;
    private final AccountShorthandParser shorthandParser;

    public FileIngestionService(IngestService ingestService,
                                AccountShorthandParser shorthandParser) {
        this.ingestService = ingestService;
        this.shorthandParser = shorthandParser;
    }

    public void scanAndIngest(Path input) throws IOException {
        log.info("Scanning directory {}", input.toAbsolutePath());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(input, "*.csv")) {
            for (Path file : stream) {
                log.info("Found file {}", file);
                String shorthand = shorthandParser.extract(file);
                if (shorthand == null) {
                    log.warn("Skipping file {} with unrecognized name", file);
                    continue;
                }
                ingestFile(file, shorthand);
            }
        }
    }

    public void ingestFile(Path file, String shorthand) throws IOException {
        if (!Files.exists(file)) {
            log.warn("File {} does not exist, skipping", file);
            return;
        }
        Path targetDir;
        try {
            ingestService.ingestFile(file, shorthand);
            log.info("Ingestion succeeded for file {}", file);
            targetDir = file.getParent().resolve("processed");
        } catch (NoSuchFileException e) {
            log.warn("File {} disappeared before it could be ingested", file);
            return;
        } catch (Exception e) {
            log.info("Ingestion failed for file {}", file, e);
            targetDir = file.getParent().resolve("error");
        }
        try {
            Files.createDirectories(targetDir);
            Files.move(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchFileException e) {
            log.warn("File {} disappeared before it could be moved to {}", file, targetDir);
        }
    }
}
