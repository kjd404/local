package com.example.ingest;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestServiceTest {
    @Test
    void routesByFilenameAndCreatesAccounts(@TempDir Path dir) throws Exception {
        MockDataProvider provider = ctx -> new MockResult[0];
        DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
        AccountResolver resolver = mock(AccountResolver.class);
        TransactionCsvReader chReader = mock(TransactionCsvReader.class);
        TransactionCsvReader coReader = mock(TransactionCsvReader.class);
        when(chReader.institution()).thenReturn("ch");
        when(coReader.institution()).thenReturn("co");
        TransactionRecord dummy = new GenericTransaction("id", null, null, 1, "USD", "m", "c", null, null, "h", "{}");
        when(chReader.read(any(), any(), eq("1234"))).thenReturn(List.of(dummy));
        when(coReader.read(any(), any(), eq("1828"))).thenReturn(List.of(dummy));
        when(resolver.resolve("ch1234")).thenReturn(new ResolvedAccount(1L, "ch", "1234"));
        when(resolver.resolve("co1828")).thenReturn(new ResolvedAccount(2L, "co", "1828"));

        Files.writeString(dir.resolve("ch1234-example.csv"), "id,amount\n1,10");
        Files.writeString(dir.resolve("co1828-example.csv"), "id,amount\n1,10");

        IngestService service = new IngestService(dsl, resolver, List.of(chReader, coReader));
        service.scanAndIngest(dir);

        verify(chReader).read(eq(dir.resolve("ch1234-example.csv")), any(), eq("1234"));
        verify(coReader).read(eq(dir.resolve("co1828-example.csv")), any(), eq("1828"));
        verify(resolver).resolve("ch1234");
        verify(resolver).resolve("co1828");
    }
}
