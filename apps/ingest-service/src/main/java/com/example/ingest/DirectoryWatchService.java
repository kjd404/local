package com.example.ingest;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DirectoryWatchService {
    private static final Logger log = LoggerFactory.getLogger(DirectoryWatchService.class);

    private final IngestService ingestService;
    private final Path directory;
    private final ExecutorService executor;
    private WatchService watchService;

    public DirectoryWatchService(IngestService ingestService, @Value("${INGEST_DIR:/incoming}") String dir) {
        this.ingestService = ingestService;
        this.directory = Paths.get(dir);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("directory-watch");
            return t;
        });
    }

    @PostConstruct
    public void start() throws IOException {
        Files.createDirectories(directory);
        watchService = FileSystems.getDefault().newWatchService();
        directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
        executor.submit(this::processEvents);
    }

    @PreDestroy
    public void stop() throws IOException {
        executor.shutdownNow();
        if (watchService != null) {
            watchService.close();
        }
    }

    private void processEvents() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        Path filename = (Path) event.context();
                        if (filename.toString().endsWith(".csv")) {
                            handleFile(filename);
                        }
                    }
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Watch service error", e);
            }
        }
    }

    private void handleFile(Path filename) throws IOException, com.opencsv.exceptions.CsvException {
        Path file = directory.resolve(filename);
        ingestService.ingestFile(file);
        Path processed = directory.resolve("processed");
        Files.createDirectories(processed);
        Files.move(file, processed.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
    }
}

