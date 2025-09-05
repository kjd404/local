package org.artificers.ingest;

import org.artificers.jooq.tables.Transactions;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.exception.DataAccessException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Repository for transaction persistence. */
public class TransactionRepository {
    public void upsert(DSLContext ctx, TransactionRecord t, ResolvedAccount account) {
        try {
            ctx.insertInto(Transactions.TRANSACTIONS)
                    .set(Transactions.TRANSACTIONS.ACCOUNT_ID, account.id())
                    .set(Transactions.TRANSACTIONS.OCCURRED_AT, toOffsetDateTime(t.occurredAt()))
                    .set(Transactions.TRANSACTIONS.POSTED_AT, toOffsetDateTime(t.postedAt()))
                    .set(Transactions.TRANSACTIONS.AMOUNT_CENTS, t.amountCents())
                    .set(Transactions.TRANSACTIONS.CURRENCY, t.currency())
                    .set(Transactions.TRANSACTIONS.MERCHANT, t.merchant())
                    .set(Transactions.TRANSACTIONS.CATEGORY, t.category())
                    .set(Transactions.TRANSACTIONS.TXN_TYPE, t.type())
                    .set(Transactions.TRANSACTIONS.MEMO, t.memo())
                    .set(Transactions.TRANSACTIONS.HASH, t.hash())
                    .set(Transactions.TRANSACTIONS.RAW_JSON, JSONB.valueOf(t.rawJson()))
                    .onConflict(
                            Transactions.TRANSACTIONS.ACCOUNT_ID,
                            Transactions.TRANSACTIONS.HASH
                    )
                    .doNothing()
                    .execute();
        } catch (DataAccessException e) {
            throw new TransactionIngestException(t, e);
        }
    }

    private OffsetDateTime toOffsetDateTime(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}
