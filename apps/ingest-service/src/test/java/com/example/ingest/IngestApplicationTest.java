package com.example.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestApplicationTest {
    @Test
    void ingestsFileWhenFileOptionPresent() throws Exception {
        IngestService service = mock(IngestService.class);
        when(service.ingestFile(any(), any())).thenReturn(true);
        DefaultApplicationArguments args = new DefaultApplicationArguments("--file=/tmp/ch1111.csv");
        IngestApplication app = new IngestApplication();

        boolean shouldExit = app.processArgs(service, args);

        verify(service).ingestFile(Path.of("/tmp/ch1111.csv"), "ch1111");
        assertThat(shouldExit).isTrue();
    }

    @Test
    void scansDirectoryWhenModeScanWithInput() throws Exception {
        IngestService service = mock(IngestService.class);
        DefaultApplicationArguments args = new DefaultApplicationArguments("--mode=scan", "--input=/tmp/in");
        IngestApplication app = new IngestApplication();

        boolean shouldExit = app.processArgs(service, args);

        verify(service).scanAndIngest(Path.of("/tmp/in"));
        assertThat(shouldExit).isTrue();
    }

    @Test
    void scansDefaultDirectoryWhenInputMissing() throws Exception {
        IngestService service = mock(IngestService.class);
        DefaultApplicationArguments args = new DefaultApplicationArguments("--mode=scan");
        IngestApplication app = new IngestApplication();

        boolean shouldExit = app.processArgs(service, args);

        verify(service).scanAndIngest(Path.of("/incoming"));
        assertThat(shouldExit).isTrue();
    }
}
