package org.artificers.ingest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

abstract class BaseCsvReader {
    protected Instant parseDate(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Instant.parse(v);
        } catch (DateTimeParseException e) {
            try {
                LocalDate d = LocalDate.parse(v, DateTimeFormatter.ISO_DATE);
                return d.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e2) {
                LocalDate d = LocalDate.parse(v, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                return d.atStartOfDay(ZoneOffset.UTC).toInstant();
            }
        }
    }

    protected long parseAmount(String amount) {
        if (amount == null || amount.isBlank()) return 0;
        return toCents(toBigDecimal(amount));
    }

    protected long parseAmount(String credit, String debit) {
        if (credit != null && !credit.isBlank()) {
            return toCents(toBigDecimal(credit));
        }
        if (debit != null && !debit.isBlank()) {
            return -toCents(toBigDecimal(debit));
        }
        return 0;
    }

    private BigDecimal toBigDecimal(String amount) {
        return new BigDecimal(amount.replace(",", ""));
    }

    private long toCents(BigDecimal v) {
        return v.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    protected String normalize(String h) {
        return h.toLowerCase()
                .replaceAll("[. ]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}
