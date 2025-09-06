package org.artificers.ingest.service;

import org.artificers.jooq.tables.Accounts;
import org.jooq.DSLContext;
import org.jooq.Record1;

import java.time.OffsetDateTime;

import org.artificers.ingest.model.ResolvedAccount;

public class AccountResolver {
    private final DSLContext dsl;
    private final AccountShorthandParser parser;

    public AccountResolver(DSLContext dsl, AccountShorthandParser parser) {
        this.dsl = dsl;
        this.parser = parser;
    }

    public ResolvedAccount resolve(String shorthand) {
        return resolve(this.dsl, shorthand);
    }

    public ResolvedAccount resolve(DSLContext ctx, String shorthand) {
        AccountShorthandParser.ParsedShorthand ids = parser.parse(shorthand);
        Record1<Long> existing = ctx.select(Accounts.ACCOUNTS.ID)
                .from(Accounts.ACCOUNTS)
                .where(Accounts.ACCOUNTS.INSTITUTION.eq(ids.institution())
                        .and(Accounts.ACCOUNTS.EXTERNAL_ID.eq(ids.externalId())))
                .fetchOne();
        if (existing != null) {
            return new ResolvedAccount(existing.value1(), ids.institution(), ids.externalId());
        }
        OffsetDateTime now = OffsetDateTime.now();
        long id = ctx.insertInto(Accounts.ACCOUNTS)
                .set(Accounts.ACCOUNTS.INSTITUTION, ids.institution())
                .set(Accounts.ACCOUNTS.EXTERNAL_ID, ids.externalId())
                .set(Accounts.ACCOUNTS.DISPLAY_NAME, ids.externalId())
                .set(Accounts.ACCOUNTS.CREATED_AT, now)
                .set(Accounts.ACCOUNTS.UPDATED_AT, now)
                .returning(Accounts.ACCOUNTS.ID)
                .fetchOne()
                .get(Accounts.ACCOUNTS.ID);
        return new ResolvedAccount(id, ids.institution(), ids.externalId());
    }
}
