package org.artificers.ingest;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AccountCreationIntegrationTest {
    private DSLContext dsl;
    private AccountResolver resolver;

    @BeforeEach
    void setup() {
        dsl = DSL.using("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false", "sa", "");
        dsl.execute("drop view if exists transactions_view");
        dsl.execute("drop table if exists transactions");
        dsl.execute("drop table if exists accounts");
        dsl.execute("create table accounts (id serial primary key, institution varchar not null, external_id varchar not null, display_name varchar not null, created_at timestamp, updated_at timestamp)");
        dsl.execute("create unique index on accounts(institution, external_id)");
        resolver = new AccountResolver(dsl);
    }

    private Path copyResource(String resource) throws IOException {
        Path dir = Files.createTempDirectory("acct");
        String name = resource.substring(resource.lastIndexOf('/') + 1);
        Path file = dir.resolve(name);
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    private ConfigurableCsvReader reader(String institution) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/mappings/" + institution + ".json")) {
            ConfigurableCsvReader.Mapping mapping =
                    new com.fasterxml.jackson.databind.ObjectMapper().readValue(in, ConfigurableCsvReader.Mapping.class);
            return new ConfigurableCsvReader(new com.fasterxml.jackson.databind.ObjectMapper(), mapping);
        }
    }

    @Test
    void createsAndReusesAccountsFromFilename() throws Exception {
        Path file1 = copyResource("/examples/ch1234-example.csv");
        try (Reader in = Files.newBufferedReader(file1)) {
            List<TransactionRecord> txs = reader("ch").read(file1, in, "1234");
            long id1 = resolver.resolve("ch1234").id();
            assertEquals(1, dsl.fetchCount(DSL.table("accounts")));
            assertEquals("ch", dsl.fetchValue("select institution from accounts where id = ?", id1));
            assertEquals("1234", dsl.fetchValue("select external_id from accounts where id = ?", id1));
        }

        Path file1b = copyResource("/examples/ch1234-example.csv");
        try (Reader in = Files.newBufferedReader(file1b)) {
            List<TransactionRecord> txs = reader("ch").read(file1b, in, "1234");
            long idAgain = resolver.resolve("ch1234").id();
            assertEquals(1, dsl.fetchCount(DSL.table("accounts")));
            Long existingId = dsl.fetchOne("select id from accounts where institution='ch' and external_id='1234'")
                    .get(0, Long.class);
            assertNotNull(existingId);
            assertEquals(existingId.longValue(), idAgain);
        }

        Path file2 = copyResource("/examples/co1828-example.csv");
        try (Reader in = Files.newBufferedReader(file2)) {
            List<TransactionRecord> txs = reader("co").read(file2, in, "1828");
            resolver.resolve("co1828");
        }
        assertEquals(2, dsl.fetchCount(DSL.table("accounts")));
    }
}
