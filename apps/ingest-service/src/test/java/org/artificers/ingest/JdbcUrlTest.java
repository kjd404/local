package org.artificers.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JdbcUrlTest {
    @Test
    void prefixesJdbcWhenMissing() {
        assertEquals("jdbc:postgresql://host/db", JdbcUrl.from("postgresql://host/db"));
    }

    @Test
    void leavesJdbcUrlUntouched() {
        assertEquals("jdbc:postgresql://host/db", JdbcUrl.from("jdbc:postgresql://host/db"));
    }

    @Test
    void convertsPostgresAlias() {
        assertEquals("jdbc:postgresql://host/db", JdbcUrl.from("postgres://host/db"));
    }

    @Test
    void returnsNullForNullUrl() {
        assertNull(JdbcUrl.from(null));
    }
}

