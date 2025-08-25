package com.example.poller;

import com.example.teller.TellerApi;
import com.example.teller.TellerClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountPollingServiceTest {
    private DSLContext dsl;
    private TellerClient client;
    private FakeTellerApi api;
    private TimeProvider clock;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setup() throws Exception {
        dsl = DSL.using("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false", "sa", "");
        dsl.execute("drop table if exists account_poll_state");
        dsl.execute("drop table if exists transactions");
        dsl.execute("drop table if exists accounts");
        dsl.execute("create table accounts (id bigserial primary key, institution varchar not null, external_id varchar not null, display_name varchar not null, created_at timestamp, updated_at timestamp, backfilled_at timestamp)");
        dsl.execute("create unique index on accounts(institution, external_id)");
        dsl.execute("create table account_poll_state (account_id bigint primary key, cursor varchar, updated_at timestamp)");
        dsl.execute("create table transactions (id bigserial primary key, account_id bigint not null, occurred_at timestamp, posted_at timestamp, amount_cents bigint not null, currency varchar, merchant varchar, category varchar, txn_type varchar, memo varchar, source varchar not null, hash varchar not null, raw_json text not null, created_at timestamp)");
        dsl.execute("create unique index transactions_account_hash_idx on transactions(account_id, hash)");
        dsl.execute("insert into accounts (id, institution, external_id, display_name, created_at, updated_at) values (1, 'teller', 'acc1', 'acc1', now(), now())");
        dsl.execute("insert into account_poll_state (account_id, cursor, updated_at) values (1, null, now())");
        api = new FakeTellerApi();
        client = new TellerClient(List.of("tok"), api);
        clock = java.time.OffsetDateTime::now;
        registry = new SimpleMeterRegistry();
    }

    @Test
    void paginationAndCursorPersistence() throws Exception {
        AccountPollingService svc1 = new AccountPollingService(dsl, client, clock, registry);
        svc1.poll();
        assertEquals(0, dsl.fetchCount(DSL.table("transactions")));
        String cur1 = dsl.select(DSL.field("cursor", String.class))
                .from("account_poll_state")
                .where(DSL.field("account_id").eq(1))
                .fetchOneInto(String.class);
        assertEquals("c2", cur1);

        AccountPollingService svc2 = new AccountPollingService(dsl, client, clock, registry);
        svc2.poll();
        assertEquals(0, dsl.fetchCount(DSL.table("transactions")));
        String cur2 = dsl.select(DSL.field("cursor", String.class))
                .from("account_poll_state")
                .where(DSL.field("account_id").eq(1))
                .fetchOneInto(String.class);
        assertEquals("c3", cur2);
        svc2.poll();
        assertEquals(0, dsl.fetchCount(DSL.table("transactions")));
        assertEquals(java.util.Arrays.asList(null, "c2", "c3"), api.cursorCalls);
    }

    static class FakeTellerApi implements TellerApi {
        private final ObjectMapper mapper = new ObjectMapper();
        final List<String> cursorCalls = new ArrayList<>();
        @Override
        public JsonNode listAccounts(String token) {
            return mapper.createArrayNode();
        }
        @Override
        public JsonNode listTransactions(String token, String accountId, String cursor) {
            cursorCalls.add(cursor);
            try {
                if (cursor == null) {
                    return mapper.readTree("[{\"id\":\"tx1\",\"cursor\":\"c1\",\"date\":\"2024-01-01\",\"amount\":{\"value\":100},\"description\":\"t1\",\"category\":\"cat\",\"type\":\"debit\",\"details\":{\"class\":\"note1\"}},{\"id\":\"tx2\",\"cursor\":\"c2\",\"date\":\"2024-01-02\",\"amount\":{\"value\":200},\"description\":\"t2\",\"category\":\"cat\",\"type\":\"debit\",\"details\":{\"class\":\"note2\"}}]");
                } else if ("c2".equals(cursor)) {
                    return mapper.readTree("[{\"id\":\"tx2\",\"cursor\":\"c2\",\"date\":\"2024-01-02\",\"amount\":{\"value\":200},\"description\":\"t2\",\"category\":\"cat\",\"type\":\"debit\",\"details\":{\"class\":\"note2\"}},{\"id\":\"tx3\",\"cursor\":\"c3\",\"date\":\"2024-01-03\",\"amount\":{\"value\":300},\"description\":\"t3\",\"category\":\"cat\",\"type\":\"debit\",\"details\":{\"class\":\"note3\"}}]");
                } else if ("c3".equals(cursor)) {
                    return mapper.readTree("[{\"id\":\"tx3\",\"cursor\":\"c3\",\"date\":\"2024-01-03\",\"amount\":{\"value\":300},\"description\":\"t3\",\"category\":\"cat\",\"type\":\"debit\",\"details\":{\"class\":\"note3\"}}]");
                }
                return mapper.createArrayNode();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
