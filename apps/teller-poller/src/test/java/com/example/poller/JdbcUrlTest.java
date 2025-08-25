package com.example.poller;

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
    void sanitizeRemovesCredentials() {
        String url = "jdbc:postgresql://user:secret@host:5432/db";
        assertEquals("jdbc:postgresql://host:5432/db", JdbcUrl.sanitize(url));
    }
}
