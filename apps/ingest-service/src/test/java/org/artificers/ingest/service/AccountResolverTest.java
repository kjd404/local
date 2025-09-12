package org.artificers.ingest.service;

import static org.junit.jupiter.api.Assertions.*;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

public class AccountResolverTest {
  private DSLContext initDsl() {
    DSLContext dsl = DSL.using("jdbc:h2:mem:test;MODE=PostgreSQL", "sa", "");
    dsl.execute("drop view if exists transactions_view");
    dsl.execute("drop table if exists transactions");
    dsl.execute("drop table if exists accounts");
    dsl.execute(
        "create table accounts (id serial primary key, institution varchar not null, external_id"
            + " varchar not null, display_name varchar not null, created_at timestamp, updated_at"
            + " timestamp)");
    dsl.execute("create unique index on accounts(institution, external_id)");
    return dsl;
  }

  @Test
  void insertsWhenMissing() {
    DSLContext dsl = initDsl();
    AccountShorthandParser parser = new AccountShorthandParser();
    AccountResolver resolver = new AccountResolver(dsl, parser);
    long id1 = resolver.resolve("bank1234").id();
    long id2 = resolver.resolve("bank1234").id();
    assertEquals(id1, id2);
  }

  @Test
  void throwsOnInvalidShorthand() {
    DSLContext dsl = initDsl();
    AccountShorthandParser parser = new AccountShorthandParser();
    AccountResolver resolver = new AccountResolver(dsl, parser);
    assertThrows(IllegalArgumentException.class, () -> resolver.resolve("invalid"));
  }
}
