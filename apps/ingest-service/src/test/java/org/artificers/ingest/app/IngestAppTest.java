package org.artificers.ingest.app;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import org.artificers.ingest.app.IngestApp;
import org.artificers.ingest.config.IngestConfig;
import org.artificers.ingest.service.AccountShorthandParser;
import org.artificers.ingest.service.DirectoryWatchService;
import org.artificers.ingest.service.FileIngestionService;
import org.artificers.ingest.service.IngestService;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestAppTest {
    @Test
    void ingestsFileWhenFileOptionPresent() throws Exception {
        IngestService service = mock(IngestService.class);
        FileIngestionService fileService = mock(FileIngestionService.class);
        doNothing().when(service).ingestFile(any(), any());
        DirectoryWatchService watch = mock(DirectoryWatchService.class);
        IngestConfig cfg = new IngestConfig(Path.of("storage/incoming"), Path.of("cfg"));
        AccountShorthandParser parser = new AccountShorthandParser();

        CommandLine cmd = new CommandLine(new IngestApp(service, fileService, watch, cfg, parser));
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        int code = cmd.execute("--file=/tmp/ch1234.csv");

        verify(service).ingestFile(Path.of("/tmp/ch1234.csv"), "ch1234");
        assertThat(code).isZero();
    }

    @Test
    void scansDirectoryWhenModeScanWithInput() throws Exception {
        IngestService service = mock(IngestService.class);
        FileIngestionService fileService = mock(FileIngestionService.class);
        DirectoryWatchService watch = mock(DirectoryWatchService.class);
        IngestConfig cfg = new IngestConfig(Path.of("storage/incoming"), Path.of("cfg"));
        AccountShorthandParser parser = new AccountShorthandParser();

        CommandLine cmd = new CommandLine(new IngestApp(service, fileService, watch, cfg, parser));
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        int code = cmd.execute("--mode=scan", "--input=/tmp/in");

        verify(fileService).scanAndIngest(Path.of("/tmp/in"));
        assertThat(code).isZero();
    }

    @Test
    void scansDefaultDirectoryWhenInputMissing() throws Exception {
        IngestService service = mock(IngestService.class);
        FileIngestionService fileService = mock(FileIngestionService.class);
        DirectoryWatchService watch = mock(DirectoryWatchService.class);
        IngestConfig cfg = new IngestConfig(Path.of("storage/incoming"), Path.of("cfg"));
        AccountShorthandParser parser = new AccountShorthandParser();

        CommandLine cmd = new CommandLine(new IngestApp(service, fileService, watch, cfg, parser));
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        int code = cmd.execute("--mode=scan");

        verify(fileService).scanAndIngest(Path.of("storage/incoming"));
        assertThat(code).isZero();
    }
}
