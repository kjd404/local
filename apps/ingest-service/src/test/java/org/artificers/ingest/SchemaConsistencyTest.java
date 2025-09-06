package org.artificers.ingest;

import org.artificers.jooq.tables.Transactions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaConsistencyTest {
    @Test
    void databaseColumnTypesMatchExpectations() {
        assertThat(Transactions.TRANSACTIONS.AMOUNT_CENTS.getDataType().getType())
                .isEqualTo(Long.class);
        assertThat(Transactions.TRANSACTIONS.CURRENCY.getDataType().getType())
                .isEqualTo(String.class);
    }
}
