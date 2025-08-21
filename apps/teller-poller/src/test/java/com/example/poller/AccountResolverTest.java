package com.example.poller;

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
        Transaction t = new Transaction();
        t.accountId = "1234";
        t.source = "bank";
        long id1 = resolver.resolve(List.of(t), Path.of("bank-1234.csv"));
        long id2 = resolver.resolve(List.of(t), Path.of("bank-1234.csv"));
        assertEquals(id1, id2);
    }

    @Test
    void throwsOnAmbiguousAccount() {
        DSLContext dsl = initDsl();
        AccountResolver resolver = new AccountResolver(dsl);
        Transaction t1 = new Transaction();
        t1.accountId = "1111";
        Transaction t2 = new Transaction();
        t2.accountId = "2222";
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(List.of(t1, t2), Path.of("bank-1111.csv")));
    }

    @Test
    void throwsOnMissingIdentifiers() {
        DSLContext dsl = initDsl();
        AccountResolver resolver = new AccountResolver(dsl);
        Transaction t = new Transaction();
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(List.of(t), Path.of("unknown.csv")));
    }
}
