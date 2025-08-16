package com.example.ingest;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AccountCreationIntegrationTest {
    private DSLContext dsl;
    private AccountResolver resolver;
    private CsvTransactionMapper mapper;

    @BeforeEach
    void setup() {
        dsl = DSL.using("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false", "sa", "");
        dsl.execute("drop table if exists accounts");
        dsl.execute("create table accounts (id serial primary key, institution varchar not null, external_id varchar not null, display_name varchar not null, created_at timestamp, updated_at timestamp)");
        dsl.execute("create unique index on accounts(institution, external_id)");
        resolver = new AccountResolver(dsl);
        mapper = new CsvTransactionMapper(resolver);
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

    @Test
    void createsAndReusesAccountsFromFilename() throws Exception {
        Path file1 = copyResource("/com/example/ingest/bank-1111.csv");
        try (Reader reader = Files.newBufferedReader(file1)) {
            List<Transaction> txs = mapper.parse(file1, reader, Map.of());
            long id1 = txs.get(0).accountPk;
            assertEquals(1, dsl.fetchCount(DSL.table("accounts")));
            assertEquals("bank", dsl.fetchValue("select institution from accounts where id = ?", id1));
            assertEquals("1111", dsl.fetchValue("select external_id from accounts where id = ?", id1));
        }

        // Re-ingest same account
        Path file1b = copyResource("/com/example/ingest/bank-1111.csv");
        try (Reader reader = Files.newBufferedReader(file1b)) {
            List<Transaction> txs = mapper.parse(file1b, reader, Map.of());
            long idAgain = txs.get(0).accountPk;
            assertEquals(1, dsl.fetchCount(DSL.table("accounts")));
            // Should match existing ID
            Long existingId = dsl.fetchOne("select id from accounts where institution='bank' and external_id='1111'")
                    .get(0, Long.class);
            assertNotNull(existingId);
            assertEquals(existingId.longValue(), idAgain);
        }

        // Ingest second account
        Path file2 = copyResource("/com/example/ingest/bank-2222.csv");
        try (Reader reader = Files.newBufferedReader(file2)) {
            mapper.parse(file2, reader, Map.of());
        }
        assertEquals(2, dsl.fetchCount(DSL.table("accounts")));
    }

    @Test
    void resolvesUsingDefaultsWhenFilenameMissing() throws Exception {
        Path file = copyResource("/com/example/ingest/mystery.csv");
        Map<String, String> defaults = Map.of("source", "otherbank", "account_id", "9999");
        try (Reader reader = Files.newBufferedReader(file)) {
            List<Transaction> txs = mapper.parse(file, reader, defaults);
            long id = txs.get(0).accountPk;
            assertEquals("otherbank", dsl.fetchValue("select institution from accounts where id = ?", id));
            assertEquals("9999", dsl.fetchValue("select external_id from accounts where id = ?", id));
        }
    }
}
