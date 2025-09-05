package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IngestServiceViewTest {
    private ConfigurableCsvReader reader(String institution) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/mappings/" + institution + ".json")) {
            ConfigurableCsvReader.Mapping mapping =
                    new ObjectMapper().readValue(in, ConfigurableCsvReader.Mapping.class);
            return new ConfigurableCsvReader(new ObjectMapper(), mapping);
        }
    }

    private Path copyResource(String resource, Path dir) throws Exception {
        Path file = dir.resolve(resource.substring(resource.lastIndexOf('/') + 1));
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    @ParameterizedTest
    @CsvSource({
            "ch,ch1234-example.csv,1234,0",
            "co,co1828-example.csv,1828,58588"
    })
    void ingestsTransactionsAndRebuildsView(String institution, String fileName, String externalId,
                                            long sum, @TempDir Path dir) throws Exception {
        DSLContext dsl = DSL.using("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false", "sa", "");

        dsl.execute("drop view if exists transactions_view");
        dsl.execute("drop table if exists transactions");
        dsl.execute("drop table if exists accounts");

        dsl.execute("create table accounts (id serial primary key, institution varchar not null, " +
                "external_id varchar not null, display_name varchar not null, created_at timestamp, updated_at timestamp)");
        dsl.execute("create unique index on accounts(institution, external_id)");
        dsl.execute("create table transactions (id serial primary key, account_id bigint not null references accounts(id), " +
                "occurred_at timestamp, posted_at timestamp, amount_cents bigint not null, currency varchar not null, merchant varchar, " +
                "category varchar, txn_type varchar, memo varchar, hash varchar not null, raw_json clob)");
        dsl.execute("create unique index on transactions(account_id, hash)");

        ConfigurableCsvReader reader = reader(institution);
        AccountResolver resolver = new AccountResolver(dsl);
        TransactionRepository repo = new TransactionRepository();
        MaterializedViewRefresher refresher = new MaterializedViewRefresher(dsl);
        IngestService service = new IngestService(dsl, resolver, Set.of(reader), repo, refresher);

        Path file = copyResource("/examples/" + fileName, dir);
        boolean ok = service.ingestFile(file, institution + externalId);

        assertThat(ok).isTrue();
        assertThat(
                dsl.fetchCount(
                        DSL.table("transactions"),
                        DSL.field("amount_cents").ne(0).or(DSL.field("merchant").isNotNull())
                )
        ).isEqualTo(2);
        long total = dsl.select(DSL.coalesce(DSL.sum(DSL.field("amount_cents", Long.class)), 0L))
                .from("transactions")
                .fetchOne(0, Long.class);
        assertThat(total).isEqualTo(sum);

        dsl.execute("create view transactions_view as select t.*, a.institution from transactions t " +
                "join accounts a on a.id = t.account_id");
        assertThat(
                dsl.fetchCount(
                        DSL.table("transactions_view"),
                        DSL.field("amount_cents").ne(0).or(DSL.field("merchant").isNotNull())
                )
        ).isEqualTo(2);
        long viewTotal = dsl.select(DSL.coalesce(DSL.sum(DSL.field("amount_cents", Long.class)), 0L))
                .from("transactions_view")
                .fetchOne(0, Long.class);
        assertThat(viewTotal).isEqualTo(sum);
    }
}

