package org.artificers.ingest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DirectoryWatchServiceIntegrationTest {
    private DirectoryWatchService watcher;

    @AfterEach
    void cleanup() throws Exception {
        if (watcher != null) {
            watcher.stop();
        }
    }

    @Test
    void ingestsAndMovesNewCsvFiles(@TempDir Path dir) throws Exception {
        IngestService ingestService = mock(IngestService.class);
        when(ingestService.ingestFile(any(), any())).thenReturn(true);
        AccountShorthandParser parser = new AccountShorthandParser();
        FileIngestionService fileService = new FileIngestionService(ingestService, parser);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        WatchService watchService = FileSystems.getDefault().newWatchService();
        watcher = new DirectoryWatchService(fileService, dir, executor, watchService, parser);
        watcher.start();

        Path file = dir.resolve("ch1234-example.csv");
        Files.writeString(file, "id,amount\n1,10");

        Path processed = dir.resolve("processed").resolve("ch1234-example.csv");
        for (int i = 0; i < 50 && !Files.exists(processed); i++) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        verify(ingestService, timeout(5000)).ingestFile(file, "ch1234");
        assertThat(Files.exists(processed)).isTrue();
    }

    @Test
    void movesFailedFilesAndContinuesWatching(@TempDir Path dir) throws Exception {
        IngestService ingestService = mock(IngestService.class);
        when(ingestService.ingestFile(any(), any())).thenReturn(false, true);
        AccountShorthandParser parser = new AccountShorthandParser();
        FileIngestionService fileService = new FileIngestionService(ingestService, parser);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        WatchService watchService = FileSystems.getDefault().newWatchService();
        watcher = new DirectoryWatchService(fileService, dir, executor, watchService, parser);
        watcher.start();

        Path bad = dir.resolve("ch1234-bad.csv");
        Files.writeString(bad, "id,amount\n1,10");
        Path error = dir.resolve("error").resolve("ch1234-bad.csv");
        for (int i = 0; i < 50 && !Files.exists(error); i++) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        Path good = dir.resolve("ch1234-good.csv");
        Files.writeString(good, "id,amount\n1,10");
        Path processed = dir.resolve("processed").resolve("ch1234-good.csv");
        for (int i = 0; i < 50 && !Files.exists(processed); i++) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        verify(ingestService, timeout(5000).times(2)).ingestFile(any(), any());
        assertThat(Files.exists(error)).isTrue();
        assertThat(Files.exists(processed)).isTrue();
    }

    @Test
    void processesExistingFilesOnStartup(@TempDir Path dir) throws Exception {
        IngestService ingestService = mock(IngestService.class);
        when(ingestService.ingestFile(any(), any())).thenReturn(true);
        Path file = dir.resolve("ch1234-existing.csv");
        Files.writeString(file, "id,amount\n1,10");

        AccountShorthandParser parser = new AccountShorthandParser();
        FileIngestionService fileService = new FileIngestionService(ingestService, parser);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        WatchService watchService = FileSystems.getDefault().newWatchService();
        watcher = new DirectoryWatchService(fileService, dir, executor, watchService, parser);
        watcher.start();

        Path processed = dir.resolve("processed").resolve("ch1234-existing.csv");
        for (int i = 0; i < 50 && !Files.exists(processed); i++) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        verify(ingestService, timeout(5000)).ingestFile(file, "ch1234");
        assertThat(Files.exists(processed)).isTrue();
    }
}
