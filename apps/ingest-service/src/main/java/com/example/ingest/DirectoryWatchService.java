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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DirectoryWatchService {
    private static final Logger log = LoggerFactory.getLogger(DirectoryWatchService.class);

    private final IngestService ingestService;
    private final Path directory;
    private final ExecutorService executor;
    private WatchService watchService;
    private static final Pattern FILE_PATTERN = Pattern.compile("^([a-zA-Z]+\\d{4}).*\\.csv$");

    public DirectoryWatchService(IngestService ingestService, @Value("${INGEST_DIR:storage/incoming}") String dir) {
        this.ingestService = ingestService;
        this.directory = Paths.get(dir).toAbsolutePath();
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
        log.info("Watching directory {} for new files", directory);
        watchService = FileSystems.getDefault().newWatchService();
        directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
        scanDirectory();
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
                log.debug("Polling {} for changes", directory);
                WatchKey key = watchService.poll(5, TimeUnit.SECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            Path filename = (Path) event.context();
                            Matcher m = FILE_PATTERN.matcher(filename.toString());
                            if (m.matches()) {
                                String shorthand = m.group(1).toLowerCase();
                                handleFile(filename, shorthand);
                            }
                        }
                    }
                    key.reset();
                }
                scanDirectory();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Watch service error", e);
            }
        }
    }

    private void scanDirectory() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.csv")) {
            for (Path file : stream) {
                Matcher m = FILE_PATTERN.matcher(file.getFileName().toString());
                if (m.matches()) {
                    handleFile(file.getFileName(), m.group(1).toLowerCase());
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan directory {}", directory, e);
        }
    }

    private void handleFile(Path filename, String shorthand) {
        Path file = directory.resolve(filename);
        log.info("Processing file {} with shorthand {}", file, shorthand);
        boolean ok = ingestService.ingestFile(file, shorthand);
        log.info("Finished processing file {}: {}", file, ok ? "success" : "failure");
        Path target = directory.resolve(ok ? "processed" : "error");
        try {
            Files.createDirectories(target);
            Files.move(file, target.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to move file {} to {}", file, target, e);
        }
    }
}

