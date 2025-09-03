package com.example.ingest;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AccountResolverTest {
    private DSLContext initDsl() {
        DSLContext dsl = DSL.using("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false", "sa", "");
        dsl.execute("drop table if exists accounts");
        dsl.execute("create table accounts (id serial primary key, institution varchar not null, external_id varchar not null, display_name varchar not null, created_at timestamp, updated_at timestamp)");
        dsl.execute("create unique index on accounts(institution, external_id)");
        return dsl;
    }

    @Test
    void insertsWhenMissing() {
        DSLContext dsl = initDsl();
        AccountResolver resolver = new AccountResolver(dsl);
        TransactionRecord t = new GenericTransaction("1234", null, null, 0, null, null,
                null, null, null, "hash", "{}", "bank");
        long id1 = resolver.resolve(List.of(t), Path.of("bank-1234.csv")).id();
        long id2 = resolver.resolve(List.of(t), Path.of("bank-1234.csv")).id();
        assertEquals(id1, id2);
    }

    @Test
    void throwsOnAmbiguousAccount() {
        DSLContext dsl = initDsl();
        AccountResolver resolver = new AccountResolver(dsl);
        TransactionRecord t1 = new GenericTransaction("1111", null, null, 0, null, null,
                null, null, null, "h1", "{}", "bank");
        TransactionRecord t2 = new GenericTransaction("2222", null, null, 0, null, null,
                null, null, null, "h2", "{}", "bank");
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(List.of(t1, t2), Path.of("bank-1111.csv")));
    }

    @Test
    void throwsOnMissingIdentifiers() {
        DSLContext dsl = initDsl();
        AccountResolver resolver = new AccountResolver(dsl);
        TransactionRecord t = new GenericTransaction(null, null, null, 0, null, null,
                null, null, null, "h", "{}", null);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(List.of(t), Path.of("unknown.csv")));
    }
}
