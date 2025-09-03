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
        long id1 = resolver.resolve("bank1234").id();
        long id2 = resolver.resolve("bank1234").id();
        assertEquals(id1, id2);
    }

    @Test
    void throwsOnInvalidShorthand() {
        DSLContext dsl = initDsl();
        AccountResolver resolver = new AccountResolver(dsl);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("invalid"));
    }
}
