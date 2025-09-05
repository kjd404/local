package org.artificers.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class DirectoryWatchService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DirectoryWatchService.class);

    private final Path directory;
    private final ExecutorService executor;
    private final WatchService watchService;
    private final FileIngestionService fileService;
    private final AccountShorthandParser shorthandParser;

    public DirectoryWatchService(FileIngestionService fileService,
                                 Path dir,
                                 ExecutorService executor,
                                 WatchService watchService,
                                 AccountShorthandParser shorthandParser) {
        this.fileService = fileService;
        this.directory = dir.toAbsolutePath();
        this.executor = executor;
        this.watchService = watchService;
        this.shorthandParser = shorthandParser;
    }

    public void start() throws IOException {
        Files.createDirectories(directory);
        log.info("Watching directory {} for new files", directory);
        directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
        fileService.scanAndIngest(directory);
        executor.submit(this::processEvents);
    }

    public void stop() throws IOException {
        executor.shutdownNow();
        watchService.close();
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    private void processEvents() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                log.debug("Polling {} for changes", directory);
                WatchKey key = watchService.poll(5, TimeUnit.SECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            Path filename = (Path) event.context();
                            Path file = directory.resolve(filename);
                            String shorthand = shorthandParser.extract(file);
                            if (shorthand != null) {
                                try {
                                    fileService.ingestFile(file, shorthand);
                                } catch (IOException e) {
                                    log.error("Failed to ingest file {}", file, e);
                                }
                            }
                        }
                    }
                    key.reset();
                }
                try {
                    fileService.scanAndIngest(directory);
                } catch (IOException e) {
                    log.error("Failed to scan directory {}", directory, e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Watch service error", e);
            }
        }
    }
}
