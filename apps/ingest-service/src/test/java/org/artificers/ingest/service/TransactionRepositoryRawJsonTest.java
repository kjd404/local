package org.artificers.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.artificers.ingest.model.GenericTransaction;
import org.artificers.ingest.model.Money;
import org.artificers.ingest.model.ResolvedAccount;
import org.artificers.ingest.model.TransactionRecord;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class TransactionRepositoryRawJsonTest {
  @Test
  void upsertCastsRawJsonParameterToJsonb() throws Exception {
    AtomicReference<String> sql = new AtomicReference<>();
    MockDataProvider provider =
        new MockDataProvider() {
          @Override
          public MockResult[] execute(MockExecuteContext ctx) {
            sql.set(ctx.sql());
            return new MockResult[] {new MockResult(0, null)};
          }
        };
    DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
    TransactionRepository repo = new TransactionRepository();
    TransactionRecord tx =
        new GenericTransaction(
            "a", null, null, new Money(100, "USD"), "m", "c", null, null, "h", "{}");
    ResolvedAccount account = new ResolvedAccount(1, "co", "a");

    repo.upsert(dsl, tx, account);

    assertThat(sql.get().toLowerCase()).contains("insert into").contains("raw_json");
  }
}
