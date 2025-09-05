package org.artificers.ingest;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestAppTest {
    @Test
    void ingestsFileWhenFileOptionPresent() throws Exception {
        IngestService service = mock(IngestService.class);
        when(service.ingestFile(any(), any())).thenReturn(true);
        DirectoryWatchService watch = mock(DirectoryWatchService.class);
        IngestConfig cfg = new IngestConfig(Path.of("storage/incoming"), Path.of("cfg"));

        int code = new CommandLine(new IngestApp(service, watch, cfg)).execute("--file=/tmp/ch1234.csv");

        verify(service).ingestFile(Path.of("/tmp/ch1234.csv"), "ch1234");
        assertThat(code).isZero();
    }

    @Test
    void scansDirectoryWhenModeScanWithInput() throws Exception {
        IngestService service = mock(IngestService.class);
        DirectoryWatchService watch = mock(DirectoryWatchService.class);
        IngestConfig cfg = new IngestConfig(Path.of("storage/incoming"), Path.of("cfg"));

        int code = new CommandLine(new IngestApp(service, watch, cfg)).execute("--mode=scan", "--input=/tmp/in");

        verify(service).scanAndIngest(Path.of("/tmp/in"));
        assertThat(code).isZero();
    }

    @Test
    void scansDefaultDirectoryWhenInputMissing() throws Exception {
        IngestService service = mock(IngestService.class);
        DirectoryWatchService watch = mock(DirectoryWatchService.class);
        IngestConfig cfg = new IngestConfig(Path.of("storage/incoming"), Path.of("cfg"));

        int code = new CommandLine(new IngestApp(service, watch, cfg)).execute("--mode=scan");

        verify(service).scanAndIngest(Path.of("storage/incoming"));
        assertThat(code).isZero();
    }
}
