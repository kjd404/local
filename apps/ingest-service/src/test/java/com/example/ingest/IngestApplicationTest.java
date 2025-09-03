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
        when(service.ingestFile(any())).thenReturn(true);
        DefaultApplicationArguments args = new DefaultApplicationArguments("--file=/tmp/sample.csv");
        IngestApplication app = new IngestApplication();

        boolean shouldExit = app.processArgs(service, args);

        verify(service).ingestFile(Path.of("/tmp/sample.csv"));
        assertThat(shouldExit).isTrue();
    }
}
