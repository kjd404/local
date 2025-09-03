package org.artificers.ingest;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestServiceTransactionTest {
    @Test
    void rollsBackWhenUpsertFails(@TempDir Path dir) throws Exception {
        DSLContext dsl = DSL.using("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false", "sa", "");
        dsl.execute("drop table if exists accounts");
        dsl.execute("drop table if exists transactions");
        dsl.execute("create table accounts (id serial primary key, institution varchar not null, external_id varchar not null, display_name varchar not null, created_at timestamp, updated_at timestamp)");
        dsl.execute("create unique index on accounts(institution, external_id)");
        dsl.execute("create table transactions (id serial primary key, account_id bigint not null, occurred_at timestamp, posted_at timestamp, amount_cents bigint not null, currency varchar not null, merchant varchar, category varchar, txn_type varchar, memo varchar, hash varchar not null, raw_json clob)");
        dsl.execute("create unique index on transactions(account_id, hash)");

        AccountResolver resolver = new AccountResolver(dsl);
        TransactionCsvReader reader = mock(TransactionCsvReader.class);
        when(reader.institution()).thenReturn("ch");
        TransactionRecord t1 = new GenericTransaction("a", null, null, 100, "USD", "m", "c", null, null, "h1", "{}");
        TransactionRecord t2 = new GenericTransaction("a", null, null, 200, "USD", "m", "c", null, null, "h1", "{}");
        when(reader.read(any(), any(), eq("1234"))).thenReturn(List.of(t1, t2));

        Files.writeString(dir.resolve("ch1234.csv"), "id,amount\n1,10");
        IngestService service = new IngestService(dsl, resolver, List.of(reader));
        boolean ok = service.ingestFile(dir.resolve("ch1234.csv"), "ch1234");

        assertThat(ok).isFalse();
        assertThat(dsl.fetchCount(DSL.table("transactions"))).isZero();
    }
}
