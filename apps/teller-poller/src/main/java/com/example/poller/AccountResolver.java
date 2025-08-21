package com.example.poller;

import com.example.jooq.tables.Accounts;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AccountResolver {
    private static final Pattern FILE_PATTERN = Pattern.compile("([A-Za-z0-9]+)[-_]([A-Za-z0-9]+)");
    private final DSLContext dsl;

    public AccountResolver(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long resolve(List<Transaction> txs, Path file) {
        String source = uniqueValue(
                txs.stream().map(t -> t.source).filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.toList()),
                "source");
        String external = uniqueValue(
                txs.stream().map(t -> t.accountId).filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.toList()),
                "account");

        String fileSource = null;
        String fileExternal = null;
        if (file != null) {
            Matcher m = FILE_PATTERN.matcher(file.getFileName().toString());
            if (m.find()) {
                fileSource = m.group(1);
                fileExternal = m.group(2);
            }
        }

        if (source == null) {
            source = fileSource;
        } else if (fileSource != null && !fileSource.equals(source)) {
            throw new IllegalArgumentException("Ambiguous source identifiers");
        }

        if (external == null) {
            external = fileExternal;
        } else if (fileExternal != null && !fileExternal.equals(external)) {
            throw new IllegalArgumentException("Ambiguous account identifiers");
        }

        if (source == null || external == null) {
            throw new IllegalArgumentException("Missing account identifiers");
        }

        Record1<Long> existing = dsl.select(Accounts.ACCOUNTS.ID)
                .from(Accounts.ACCOUNTS)
                .where(Accounts.ACCOUNTS.INSTITUTION.eq(source)
                        .and(Accounts.ACCOUNTS.EXTERNAL_ID.eq(external)))
                .fetchOne();
        if (existing != null) {
            return existing.value1();
        }

        OffsetDateTime now = OffsetDateTime.now();
        return dsl.insertInto(Accounts.ACCOUNTS)
                .set(Accounts.ACCOUNTS.INSTITUTION, source)
                .set(Accounts.ACCOUNTS.EXTERNAL_ID, external)
                .set(Accounts.ACCOUNTS.DISPLAY_NAME, external)
                .set(Accounts.ACCOUNTS.CREATED_AT, now)
                .set(Accounts.ACCOUNTS.UPDATED_AT, now)
                .returning(Accounts.ACCOUNTS.ID)
                .fetchOne()
                .get(Accounts.ACCOUNTS.ID);
    }

    private String uniqueValue(List<String> values, String field) {
        if (values.isEmpty()) return null;
        String first = values.get(0);
        for (String v : values) {
            if (!first.equals(v)) {
                throw new IllegalArgumentException("Ambiguous " + field + " identifiers");
            }
        }
        return first;
    }
}
