package com.example.poller;

import com.example.teller.TellerApi;
import com.example.teller.TellerClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountPollingServiceTest {

    private DSLContext dsl(MockDataProvider provider) {
        return DSL.using(new MockConnection(provider), org.jooq.SQLDialect.POSTGRES);
    }

    static class FixedTimeProvider implements TimeProvider {
        private final OffsetDateTime fixed;
        FixedTimeProvider(OffsetDateTime fixed) { this.fixed = fixed; }
        @Override public OffsetDateTime now() { return fixed; }
    }

    static TellerClient dummyClient() {
        TellerApi api = new TellerApi() {
            @Override public JsonNode listAccounts(String token) { return null; }
            @Override public JsonNode listTransactions(String token, String accountId, String cursor) { return null; }
        };
        return new TellerClient(List.of("tok"), api);
    }

    @Test
    void backfillDbFailure() {
        MockDataProvider provider = ctx -> { throw new DataAccessException("fail") {}; };
        AccountPollingService svc = new AccountPollingService(dsl(provider), dummyClient(), new FixedTimeProvider(OffsetDateTime.parse("2024-01-01T00:00:00Z")));
        assertThrows(DataAccessException.class, svc::backfill);
    }
    @Test
    void pollDbFailure() {
        MockDataProvider provider = ctx -> { throw new DataAccessException("fail") {}; };
        AccountPollingService svc = new AccountPollingService(dsl(provider), dummyClient(), new FixedTimeProvider(OffsetDateTime.parse("2024-01-01T00:00:00Z")));
        assertThrows(DataAccessException.class, svc::poll);
    }

    @Test
    void persistTransactionsReturnsCursor() throws Exception {
        MockDataProvider provider = ctx -> new MockResult[]{ new MockResult(1, null) };
        AccountPollingService svc = new AccountPollingService(dsl(provider), dummyClient(), new FixedTimeProvider(OffsetDateTime.parse("2024-01-01T00:00:00Z")));
        JsonNode txs = new ObjectMapper().readTree("[{\"id\":\"t1\",\"amount\":{\"value\":1},\"cursor\":\"next\"}]");
        String cursor = svc.persistTransactions(1L, "ext", txs);
        assertEquals("next", cursor);
    }

    @Test
    void persistTransactionsWithNullReturnsNull() {
        MockDataProvider provider = ctx -> new MockResult[]{ new MockResult(1, null) };
        AccountPollingService svc = new AccountPollingService(dsl(provider), dummyClient(), new FixedTimeProvider(OffsetDateTime.parse("2024-01-01T00:00:00Z")));
        assertNull(svc.persistTransactions(1L, "ext", null));
    }

    @Test
    void upsertCursorSuccess() {
        List<String> sqls = new ArrayList<>();
        MockDataProvider provider = ctx -> { sqls.add(ctx.sql()); return new MockResult[]{ new MockResult(1, null) }; };
        AccountPollingService svc = new AccountPollingService(dsl(provider), dummyClient(), new FixedTimeProvider(OffsetDateTime.parse("2024-01-01T00:00:00Z")));
        assertDoesNotThrow(() -> svc.upsertCursor(1L, "c"));
        assertFalse(sqls.isEmpty());
    }

    @Test
    void upsertCursorFailure() {
        MockDataProvider provider = ctx -> { throw new DataAccessException("fail") {}; };
        AccountPollingService svc = new AccountPollingService(dsl(provider), dummyClient(), new FixedTimeProvider(OffsetDateTime.parse("2024-01-01T00:00:00Z")));
        assertThrows(DataAccessException.class, () -> svc.upsertCursor(1L, "c"));
    }

    @Test
    void upsertSuccess() {
        MockDataProvider provider = ctx -> new MockResult[]{ new MockResult(1, null) };
        AccountPollingService svc = new AccountPollingService(dsl(provider), dummyClient(), new FixedTimeProvider(OffsetDateTime.parse("2024-01-01T00:00:00Z")));
        Transaction t = new Transaction();
        t.accountPk = 1L;
        t.hash = "h";
        assertDoesNotThrow(() -> svc.upsert(t));
    }

    @Test
    void upsertFailure() {
        MockDataProvider provider = ctx -> { throw new DataAccessException("fail") {}; };
        AccountPollingService svc = new AccountPollingService(dsl(provider), dummyClient(), new FixedTimeProvider(OffsetDateTime.parse("2024-01-01T00:00:00Z")));
        Transaction t = new Transaction();
        t.accountPk = 1L;
        t.hash = "h";
        assertThrows(DataAccessException.class, () -> svc.upsert(t));
    }

    @Test
    void toTsWorks() {
        MockDataProvider provider = ctx -> new MockResult[]{ new MockResult(1, null) };
        AccountPollingService svc = new AccountPollingService(dsl(provider), dummyClient(), new FixedTimeProvider(OffsetDateTime.parse("2024-01-01T00:00:00Z")));
        Instant now = Instant.now();
        assertNotNull(svc.toTs(now));
        assertNull(svc.toTs(null));
    }
}
