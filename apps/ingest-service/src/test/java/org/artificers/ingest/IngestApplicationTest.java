package org.artificers.ingest;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestApplicationTest {
    @Test
    void ingestsFileWhenFileOptionPresent() throws Exception {
        IngestService service = mock(IngestService.class);
        when(service.ingestFile(any(), any())).thenReturn(true);
        String[] args = {"--file=/tmp/ch1234.csv"};

        boolean shouldExit = IngestApplication.processArgs(service, args);

        verify(service).ingestFile(Path.of("/tmp/ch1234.csv"), "ch1234");
        assertThat(shouldExit).isTrue();
    }

    @Test
    void scansDirectoryWhenModeScanWithInput() throws Exception {
        IngestService service = mock(IngestService.class);
        String[] args = {"--mode=scan", "--input=/tmp/in"};

        boolean shouldExit = IngestApplication.processArgs(service, args);

        verify(service).scanAndIngest(Path.of("/tmp/in"));
        assertThat(shouldExit).isTrue();
    }

    @Test
    void scansDefaultDirectoryWhenInputMissing() throws Exception {
        IngestService service = mock(IngestService.class);
        String[] args = {"--mode=scan"};

        boolean shouldExit = IngestApplication.processArgs(service, args);

        verify(service).scanAndIngest(Path.of("storage/incoming"));
        assertThat(shouldExit).isTrue();
    }
}
