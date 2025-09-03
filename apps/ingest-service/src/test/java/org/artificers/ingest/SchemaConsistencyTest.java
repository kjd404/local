package org.artificers.ingest;

import org.artificers.jooq.tables.Transactions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaConsistencyTest {
    @Test
    void databaseColumnTypesMatchExpectations() {
        assertThat(Transactions.TRANSACTIONS.AMOUNT_CENTS.getDataType().getType())
                .isEqualTo(Long.class);
        assertThat(Transactions.TRANSACTIONS.CURRENCY.getDataType().getType())
                .isEqualTo(String.class);
    }

    @Test
    void protoTypesMatchDatabase() throws IOException {
        Path proto = Path.of("..", "..", "ops", "proto", "finance", "v1", "ingest.proto");
        Map<String, String> fields = parseProto(proto, "Transaction");
        assertThat(fields.get("amount_cents")).isEqualTo("int64");
        assertThat(fields.get("currency")).isEqualTo("string");
    }

    private static Map<String, String> parseProto(Path proto, String message) throws IOException {
        List<String> lines = Files.readAllLines(proto);
        Map<String, String> fields = new HashMap<>();
        boolean inMsg = false;
        Pattern p = Pattern.compile("(\\w+)\\s+(\\w+)\\s*=\\s*\\d+;");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("message "+message)) { inMsg = true; continue; }
            if (inMsg) {
                if (line.startsWith("}")) break;
                Matcher m = p.matcher(line);
                if (m.find()) {
                    fields.put(m.group(2), m.group(1));
                }
            }
        }
        return fields;
    }
}
