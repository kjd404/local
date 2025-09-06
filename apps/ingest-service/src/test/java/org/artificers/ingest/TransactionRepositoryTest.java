package org.artificers.ingest;

import org.artificers.jooq.tables.Accounts;
import org.artificers.jooq.tables.Transactions;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionRepositoryTest {
    private DSLContext dsl;

    @BeforeEach
    void setup() {
        dsl = DSL.using("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false", "sa", "");
        dsl.execute("create domain if not exists jsonb as varchar");
        dsl.execute("drop table if exists transactions");
        dsl.execute("drop table if exists accounts");
        dsl.execute("create table accounts (id bigserial primary key, institution varchar not null, external_id varchar not null, display_name varchar not null, created_at timestamp, updated_at timestamp)");
        dsl.execute("create table transactions (id bigserial primary key, account_id bigint not null, occurred_at timestamp with time zone, posted_at timestamp with time zone, amount_cents bigint not null, currency varchar not null, merchant varchar, category varchar, memo varchar, txn_type varchar, hash varchar not null, raw_json jsonb not null, created_at timestamp with time zone, unique(account_id, hash))");
        dsl.insertInto(Accounts.ACCOUNTS)
                .set(Accounts.ACCOUNTS.ID, 1L)
                .set(Accounts.ACCOUNTS.INSTITUTION, "co")
                .set(Accounts.ACCOUNTS.EXTERNAL_ID, "1234")
                .set(Accounts.ACCOUNTS.DISPLAY_NAME, "1234")
                .set(Accounts.ACCOUNTS.CREATED_AT, OffsetDateTime.now())
                .set(Accounts.ACCOUNTS.UPDATED_AT, OffsetDateTime.now())
                .execute();
    }

    @Test
    void upsertIgnoresDuplicatesOnHash() {
        TransactionRepository repo = new TransactionRepository();
        ResolvedAccount account = new ResolvedAccount(1L, "co", "1234");
        TransactionRecord t1 = new GenericTransaction("1234", null, null, new Money(100, "USD"), "m", null, null, null, "h1", "{}");
        TransactionRecord t2 = new GenericTransaction("1234", null, null, new Money(200, "USD"), "m", null, null, null, "h1", "{}");
        repo.upsert(dsl, t1, account);
        repo.upsert(dsl, t2, account);
        assertEquals(1, dsl.fetchCount(Transactions.TRANSACTIONS));
        TransactionRecord t3 = new GenericTransaction("1234", null, null, new Money(300, "USD"), "m", null, null, null, "h2", "{}");
        repo.upsert(dsl, t3, account);
        assertEquals(2, dsl.fetchCount(Transactions.TRANSACTIONS));
    }
}

