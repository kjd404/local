package org.artificers.ingest;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IngestServiceRawJsonTest {
    @Test
    void upsertCastsRawJsonParameterToJsonb() throws Exception {
        AtomicReference<String> sql = new AtomicReference<>();
        MockDataProvider provider = new MockDataProvider() {
            @Override
            public MockResult[] execute(MockExecuteContext ctx) {
                sql.set(ctx.sql());
                return new MockResult[] { new MockResult(0, null) };
            }
        };
        DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
        IngestService service = new IngestService(dsl, null, List.of());
        TransactionRecord tx = new GenericTransaction("a", null, null, 100, "USD", "m", "c", null, null, "h", "{}");
        ResolvedAccount account = new ResolvedAccount(1, "co", "a");

        Method m = IngestService.class.getDeclaredMethod("upsert", DSLContext.class, TransactionRecord.class, ResolvedAccount.class);
        m.setAccessible(true);
        m.invoke(service, dsl, tx, account);

        assertThat(sql.get()).contains("cast(? as jsonb)");
    }
}
