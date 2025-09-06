package org.artificers.ingest;

import org.junit.jupiter.api.Test;
import org.artificers.ingest.model.Account;

import java.lang.reflect.Modifier;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class AccountTest {
    @Test
    void isImmutableRecord() {
        Instant now = Instant.now();
        Account account = new Account(1L, "inst", "ext", "name", now, now);

        assertEquals(1L, account.id());
        assertEquals("inst", account.institution());
        assertEquals("ext", account.externalId());
        assertEquals("name", account.displayName());
        assertEquals(now, account.createdAt());
        assertEquals(now, account.updatedAt());

        assertTrue(Account.class.isRecord(), "Account should be a record");
        assertTrue(Modifier.isFinal(Account.class.getModifiers()), "Account should be final");
        for (var field : Account.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()), "Field " + field.getName() + " should be private");
            assertTrue(Modifier.isFinal(field.getModifiers()), "Field " + field.getName() + " should be final");
        }
    }
}
