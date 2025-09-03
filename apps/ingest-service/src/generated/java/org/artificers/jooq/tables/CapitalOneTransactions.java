package org.artificers.jooq.tables;

import java.time.OffsetDateTime;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

public class CapitalOneTransactions extends TableImpl<Record> {
    public static final CapitalOneTransactions CAPITAL_ONE_TRANSACTIONS = new CapitalOneTransactions();

    public final TableField<Record, Long> ID = createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this, "");
    public final TableField<Record, OffsetDateTime> OCCURRED_AT = createField(DSL.name("occurred_at"), SQLDataType.TIMESTAMPWITHTIMEZONE, this, "");
    public final TableField<Record, OffsetDateTime> POSTED_AT = createField(DSL.name("posted_at"), SQLDataType.TIMESTAMPWITHTIMEZONE, this, "");
    public final TableField<Record, Long> AMOUNT_CENTS = createField(DSL.name("amount_cents"), SQLDataType.BIGINT.nullable(false), this, "");
    public final TableField<Record, String> CURRENCY = createField(DSL.name("currency"), SQLDataType.VARCHAR.nullable(false), this, "");
    public final TableField<Record, String> MERCHANT = createField(DSL.name("merchant"), SQLDataType.VARCHAR, this, "");
    public final TableField<Record, String> CATEGORY = createField(DSL.name("category"), SQLDataType.VARCHAR, this, "");
    public final TableField<Record, String> TXN_TYPE = createField(DSL.name("txn_type"), SQLDataType.VARCHAR, this, "");
    public final TableField<Record, String> MEMO = createField(DSL.name("memo"), SQLDataType.VARCHAR, this, "");
    public final TableField<Record, String> HASH = createField(DSL.name("hash"), SQLDataType.VARCHAR.nullable(false), this, "");
    public final TableField<Record, JSONB> RAW_JSON = createField(DSL.name("raw_json"), SQLDataType.JSONB.nullable(false), this, "");
    public final TableField<Record, OffsetDateTime> CREATED_AT = createField(DSL.name("created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE, this, "");

    private CapitalOneTransactions() {
        super(DSL.name("capital_one_transactions"));
    }
}
