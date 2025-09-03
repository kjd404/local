package org.artificers.ingest;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestServiceTransactionTest {
    @ParameterizedTest
    @CsvSource({"ch,chase_transactions", "co,capital_one_transactions"})
    void ignoresDuplicateTransactions(String institution, String table, @TempDir Path dir) throws Exception {
        DSLContext dsl = DSL.using("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false", "sa", "");
        dsl.execute("drop table if exists accounts");
        dsl.execute("drop table if exists " + table);
        dsl.execute("create table accounts (id serial primary key, institution varchar not null, external_id varchar not null, display_name varchar not null, created_at timestamp, updated_at timestamp)");
        dsl.execute("create unique index on accounts(institution, external_id)");
        dsl.execute("create table " + table + " (id serial primary key, occurred_at timestamp, posted_at timestamp, amount_cents bigint not null, currency varchar not null, merchant varchar, category varchar, txn_type varchar, memo varchar, hash varchar not null, raw_json clob)");
        dsl.execute("create unique index on " + table + "(hash)");

        AccountResolver resolver = new AccountResolver(dsl);
        TransactionCsvReader reader = mock(TransactionCsvReader.class);
        when(reader.institution()).thenReturn(institution);
        TransactionRecord t1 = new GenericTransaction("a", null, null, 100, "USD", "m", "c", null, null, "h1", "{}");
        TransactionRecord t2 = new GenericTransaction("a", null, null, 200, "USD", "m", "c", null, null, "h1", "{}");
        when(reader.read(any(), any(), eq("1234"))).thenReturn(List.of(t1, t2));

        Files.writeString(dir.resolve(institution + "1234.csv"), "id,amount\n1,10");
        IngestService service = new IngestService(dsl, resolver, List.of(reader));
        boolean ok = service.ingestFile(dir.resolve(institution + "1234.csv"), institution + "1234");

        assertThat(ok).isTrue();
        assertThat(dsl.fetchCount(DSL.table(table))).isEqualTo(1);
    }
}
