package com.example.ingest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class DirectoryWatchServiceTest {
    private DirectoryWatchService watcher;

    @AfterEach
    void cleanup() throws Exception {
        if (watcher != null) {
            watcher.stop();
        }
    }

    @Test
    void ingestsAndMovesNewCsvFiles() throws Exception {
        Path dir = Files.createTempDirectory("watch");
        IngestService ingestService = mock(IngestService.class);
        watcher = new DirectoryWatchService(ingestService, dir.toString());
        watcher.start();

        Path file = dir.resolve("sample.csv");
        Files.writeString(file, "id,amount\n1,10");

        Path processed = dir.resolve("processed").resolve("sample.csv");
        for (int i = 0; i < 50 && !Files.exists(processed); i++) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        verify(ingestService, timeout(5000)).ingestFile(file);
        assertThat(Files.exists(processed)).isTrue();
    }
}

