package com.example.ingest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        when(ingestService.ingestFile(any())).thenReturn(true);
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

    @Test
    void movesFailedFilesAndContinuesWatching() throws Exception {
        Path dir = Files.createTempDirectory("watch");
        IngestService ingestService = mock(IngestService.class);
        when(ingestService.ingestFile(any())).thenReturn(false, true);
        watcher = new DirectoryWatchService(ingestService, dir.toString());
        watcher.start();

        Path bad = dir.resolve("bad.csv");
        Files.writeString(bad, "id,amount\n1,10");
        Path failed = dir.resolve("failed").resolve("bad.csv");
        for (int i = 0; i < 50 && !Files.exists(failed); i++) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        Path good = dir.resolve("good.csv");
        Files.writeString(good, "id,amount\n1,10");
        Path processed = dir.resolve("processed").resolve("good.csv");
        for (int i = 0; i < 50 && !Files.exists(processed); i++) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        verify(ingestService, timeout(5000).times(2)).ingestFile(any());
        assertThat(Files.exists(failed)).isTrue();
        assertThat(Files.exists(processed)).isTrue();
    }
}

