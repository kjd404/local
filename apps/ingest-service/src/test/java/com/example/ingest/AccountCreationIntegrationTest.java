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

import static org.junit.jupiter.api.Assertions.*;

public class AccountCreationIntegrationTest {
    private DSLContext dsl;
    private AccountResolver resolver;
    private TransactionCsvReader reader;

    @BeforeEach
    void setup() {
        dsl = DSL.using("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false", "sa", "");
        dsl.execute("drop table if exists accounts");
        dsl.execute("create table accounts (id serial primary key, institution varchar not null, external_id varchar not null, display_name varchar not null, created_at timestamp, updated_at timestamp)");
        dsl.execute("create unique index on accounts(institution, external_id)");
        resolver = new AccountResolver(dsl);
        reader = new ChaseFreedomCsvReader();
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
        Path file1 = copyResource("/com/example/ingest/ch1111.csv");
        try (Reader in = Files.newBufferedReader(file1)) {
            List<TransactionRecord> txs = reader.read(file1, in, "1111");
            long id1 = resolver.resolve("ch1111").id();
            assertEquals(1, dsl.fetchCount(DSL.table("accounts")));
            assertEquals("ch", dsl.fetchValue("select institution from accounts where id = ?", id1));
            assertEquals("1111", dsl.fetchValue("select external_id from accounts where id = ?", id1));
        }

        // Re-ingest same account
        Path file1b = copyResource("/com/example/ingest/ch1111.csv");
        try (Reader in = Files.newBufferedReader(file1b)) {
            List<TransactionRecord> txs = reader.read(file1b, in, "1111");
            long idAgain = resolver.resolve("ch1111").id();
            assertEquals(1, dsl.fetchCount(DSL.table("accounts")));
            Long existingId = dsl.fetchOne("select id from accounts where institution='ch' and external_id='1111'")
                    .get(0, Long.class);
            assertNotNull(existingId);
            assertEquals(existingId.longValue(), idAgain);
        }

        // Ingest second account
        Path file2 = copyResource("/com/example/ingest/ch2222.csv");
        try (Reader in = Files.newBufferedReader(file2)) {
            List<TransactionRecord> txs = reader.read(file2, in, "2222");
            resolver.resolve("ch2222");
        }
        assertEquals(2, dsl.fetchCount(DSL.table("accounts")));
    }
}
