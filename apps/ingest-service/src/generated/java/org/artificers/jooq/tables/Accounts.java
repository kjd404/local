package org.artificers.jooq.tables;

import java.time.OffsetDateTime;
import org.jooq.Record;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

public class Accounts extends TableImpl<Record> {
    public static final Accounts ACCOUNTS = new Accounts();

    public final TableField<Record, Long> ID = createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this, "");
    public final TableField<Record, String> INSTITUTION = createField(DSL.name("institution"), SQLDataType.VARCHAR.nullable(false), this, "");
    public final TableField<Record, String> EXTERNAL_ID = createField(DSL.name("external_id"), SQLDataType.VARCHAR.nullable(false), this, "");
    public final TableField<Record, String> DISPLAY_NAME = createField(DSL.name("display_name"), SQLDataType.VARCHAR.nullable(false), this, "");
    public final TableField<Record, OffsetDateTime> CREATED_AT = createField(DSL.name("created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE, this, "");
    public final TableField<Record, OffsetDateTime> UPDATED_AT = createField(DSL.name("updated_at"), SQLDataType.TIMESTAMPWITHTIMEZONE, this, "");

    private Accounts() {
        super(DSL.name("accounts"));
    }
}
