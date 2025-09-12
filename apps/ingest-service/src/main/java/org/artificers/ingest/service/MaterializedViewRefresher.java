package org.artificers.ingest.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Refreshes materialized views after ingestion. */
public class MaterializedViewRefresher {
  private static final Logger log = LoggerFactory.getLogger(MaterializedViewRefresher.class);
  private final DSLContext dsl;

  public MaterializedViewRefresher(DSLContext dsl) {
    this.dsl = dsl;
  }

  public void refreshTransactionsView() {
    try {
      boolean exists =
          dsl.fetchExists(
              DSL.selectOne()
                  .from("pg_matviews")
                  .where(DSL.field("matviewname").eq("transactions_view")));
      if (exists) {
        dsl.execute("REFRESH MATERIALIZED VIEW transactions_view");
      }
    } catch (Exception e) {
      log.debug("Skipping materialized view refresh: {}", e.getMessage());
    }
  }
}
