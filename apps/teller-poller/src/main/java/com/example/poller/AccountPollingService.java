package com.example.poller;

import com.example.teller.TellerClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Polls Teller accounts for transactions and maintains cursors.
 */
@Service
public class AccountPollingService {
    private final DSLContext dsl;
    private final TellerClient client;
    private final TimeProvider clock;

    public AccountPollingService(DSLContext dsl, TellerClient client, TimeProvider clock) {
        this.dsl = dsl;
        this.client = client;
        this.clock = clock;
    }

    @PostConstruct
    public void backfill() throws IOException, InterruptedException {
        var accounts = dsl.select(DSL.field("id", Long.class), DSL.field("external_id", String.class))
                .from("accounts")
                .where(DSL.field("backfilled_at").isNull())
                .fetch();
        for (Record acc : accounts) {
            long accountId = acc.get(0, Long.class);
            String externalId = acc.get(1, String.class);
            String cursor = null;
            do {
                JsonNode txs = client.listTransactions(client.getTokens().get(0), externalId, cursor);
                cursor = persistTransactions(accountId, externalId, txs);
            } while (cursor != null && !cursor.isBlank());
            dsl.update(DSL.table("accounts"))
                .set(DSL.field("backfilled_at"), clock.now())
                .where(DSL.field("id").eq(accountId))
                .execute();
            upsertCursor(accountId, cursor);
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void poll() throws IOException, InterruptedException {
        var states = dsl.select(DSL.field("account_id", Long.class), DSL.field("cursor", String.class))
                .from("account_poll_state")
                .fetch();
        for (Record st : states) {
            long accountId = st.get(0, Long.class);
            String cursor = st.get(1, String.class);
            Record acc = dsl.select(DSL.field("external_id", String.class))
                    .from("accounts")
                    .where(DSL.field("id").eq(accountId))
                    .fetchOne();
            if (acc == null) continue;
            String externalId = acc.get(0, String.class);
            JsonNode txs = client.listTransactions(client.getTokens().get(0), externalId, cursor);
            String next = persistTransactions(accountId, externalId, txs);
            upsertCursor(accountId, next);
        }
    }

    void upsertCursor(long accountId, String cursor) {
        dsl.insertInto(DSL.table("account_poll_state"))
                .set(DSL.field("account_id"), accountId)
                .set(DSL.field("cursor"), cursor)
                .set(DSL.field("updated_at"), clock.now())
                .onConflict(DSL.field("account_id"))
                .doUpdate()
                .set(DSL.field("cursor"), cursor)
                .set(DSL.field("updated_at"), clock.now())
                .execute();
    }

    String persistTransactions(long accountId, String externalId, JsonNode txs) {
        String cursor = null;
        if (txs == null || !txs.isArray()) return cursor;
        for (JsonNode node : txs) {
            Transaction t = new Transaction();
            t.accountPk = accountId;
            t.accountId = externalId;
            t.source = "teller";
            t.hash = node.path("id").asText();
            t.amountCents = node.path("amount").path("value").asLong();
            t.currency = node.path("amount").path("currency").asText("USD");
            String date = node.path("date").asText(null);
            if (date != null) {
                Instant inst = Instant.from(java.time.LocalDate.parse(date).atStartOfDay().atZone(ZoneOffset.UTC));
                t.occurredAt = inst;
                t.postedAt = inst;
            }
            t.merchant = node.path("description").asText(null);
            t.type = node.path("type").asText(null);
            t.memo = node.path("details").path("class").asText(null);
            t.rawJson = node.toString();
            upsert(t);
            cursor = node.path("cursor").asText(null);
        }
        return cursor;
    }

    void upsert(Transaction t) {
        dsl.insertInto(DSL.table("transactions"))
                .set(DSL.field("account_id"), t.accountPk)
                .set(DSL.field("occurred_at"), toTs(t.occurredAt))
                .set(DSL.field("posted_at"), toTs(t.postedAt))
                .set(DSL.field("amount_cents"), t.amountCents)
                .set(DSL.field("currency"), t.currency)
                .set(DSL.field("merchant"), t.merchant)
                .set(DSL.field("category"), t.category)
                .set(DSL.field("txn_type"), t.type)
                .set(DSL.field("memo"), t.memo)
                .set(DSL.field("source"), t.source)
                .set(DSL.field("hash"), t.hash)
                .set(DSL.field("raw_json"), DSL.field("?::jsonb", String.class, t.rawJson))
                .onConflict(DSL.field("account_id", Long.class), DSL.field("hash"))
                .doNothing()
                .execute();
    }

    Timestamp toTs(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }
}
