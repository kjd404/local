package org.artificers.ingest;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileIngestionServiceTest {
    @Test
    void routesByFilenameAndCreatesAccounts(@TempDir Path dir) throws Exception {
        MockDataProvider provider = ctx -> new MockResult[0];
        DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
        AccountResolver resolver = mock(AccountResolver.class);
        AccountShorthandParser parser = new AccountShorthandParser();
        TransactionCsvReader chReader = mock(TransactionCsvReader.class);
        TransactionCsvReader coReader = mock(TransactionCsvReader.class);
        when(chReader.institution()).thenReturn("ch");
        when(coReader.institution()).thenReturn("co");
        TransactionRecord dummy = new GenericTransaction("id", null, null, new Money(1, "USD"), "m", "c", null, null, "h", "{}");
        when(chReader.read(any(), any(), eq("1234"))).thenReturn(List.of(dummy));
        when(coReader.read(any(), any(), eq("1828"))).thenReturn(List.of(dummy));
        when(resolver.resolve(any(DSLContext.class), eq("ch1234"))).thenReturn(new ResolvedAccount(1L, "ch", "1234"));
        when(resolver.resolve(any(DSLContext.class), eq("co1828"))).thenReturn(new ResolvedAccount(2L, "co", "1828"));

        copyResource("ch1234-example.csv", dir.resolve("ch1234-example.csv"));
        copyResource("co1828-example.csv", dir.resolve("co1828-example.csv"));

        TransactionRepository repo = new TransactionRepository();
        MaterializedViewRefresher refresher = new MaterializedViewRefresher(dsl);
        IngestService service = new IngestService(dsl, resolver, parser, Set.of(chReader, coReader), repo, refresher);
        FileIngestionService fileService = new FileIngestionService(service, parser);
        fileService.scanAndIngest(dir);

        verify(chReader).read(eq(dir.resolve("ch1234-example.csv")), any(), eq("1234"));
        verify(coReader).read(eq(dir.resolve("co1828-example.csv")), any(), eq("1828"));
        verify(resolver).resolve(any(DSLContext.class), eq("ch1234"));
        verify(resolver).resolve(any(DSLContext.class), eq("co1828"));
    }

    @Test
    void skipsMissingFile(@TempDir Path dir) throws Exception {
        IngestService service = mock(IngestService.class);
        AccountShorthandParser parser = new AccountShorthandParser();
        FileIngestionService fileService = new FileIngestionService(service, parser);
        Path missing = dir.resolve("missing.csv");
        assertDoesNotThrow(() -> fileService.ingestFile(missing, "ch1234"));
        verifyNoInteractions(service);
    }

    private void copyResource(String resource, Path target) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/examples/" + resource)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
