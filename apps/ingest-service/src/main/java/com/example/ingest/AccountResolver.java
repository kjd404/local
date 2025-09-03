package com.example.ingest;

import com.example.jooq.tables.Accounts;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AccountResolver {
    private static final Pattern SHORTHAND = Pattern.compile("^([A-Za-z]+)(\\d{4})$");
    private static final Pattern FILE_PATTERN = Pattern.compile("^([A-Za-z]+\\d{4}).*\\.csv$");

    public static String extractShorthand(Path path) {
        Matcher m = FILE_PATTERN.matcher(path.getFileName().toString());
        return m.matches() ? m.group(1).toLowerCase() : null;
    }

    public static ParsedShorthand parse(String shorthand) {
        if (shorthand == null) throw new IllegalArgumentException("Missing account shorthand");
        Matcher m = SHORTHAND.matcher(shorthand.toLowerCase());
        if (!m.matches()) throw new IllegalArgumentException("Invalid account shorthand");
        return new ParsedShorthand(m.group(1), m.group(2));
    }

    public record ParsedShorthand(String institution, String externalId) {}

    private final DSLContext dsl;

    public AccountResolver(DSLContext dsl) { this.dsl = dsl; }

    public ResolvedAccount resolve(String shorthand) {
        ParsedShorthand ids = parse(shorthand);
        Record1<Long> existing = dsl.select(Accounts.ACCOUNTS.ID)
                .from(Accounts.ACCOUNTS)
                .where(Accounts.ACCOUNTS.INSTITUTION.eq(ids.institution())
                        .and(Accounts.ACCOUNTS.EXTERNAL_ID.eq(ids.externalId())))
                .fetchOne();
        if (existing != null) {
            return new ResolvedAccount(existing.value1(), ids.institution(), ids.externalId());
        }
        OffsetDateTime now = OffsetDateTime.now();
        long id = dsl.insertInto(Accounts.ACCOUNTS)
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

