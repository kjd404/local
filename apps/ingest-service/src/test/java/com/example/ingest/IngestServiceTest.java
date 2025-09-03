package com.example.ingest;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IngestServiceTest {
    @Test
    void returnsFalseWhenFileMissing() {
        DSLContext dsl = mock(DSLContext.class);
        AccountResolver resolver = mock(AccountResolver.class);
        IngestService service = new IngestService(dsl, resolver, List.of());

        boolean ok = service.ingestFile(Path.of("does-not-exist.csv"), "ch1111");

        assertThat(ok).isFalse();
    }
}
