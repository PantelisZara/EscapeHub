package gr.escapehub.account;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountRegistryTest {
    @Test
    void registersAccountWithSaltedPasswordHash() {
        AccountRegistry registry = new AccountRegistry();

        var first = registry.register("  Θάνος  ", "Secret123!");
        var second = registry.register("Μάνος", "Secret123!");

        assertTrue(first.created());
        assertNull(first.error());
        assertEquals("Θάνος", first.account().username());
        assertNotEquals("Secret123!", first.account().passwordHash());
        assertFalse(first.account().toString().contains(first.account().passwordHash()));
        assertTrue(PasswordHasher.verify("Secret123!", first.account().passwordHash()));
        assertFalse(PasswordHasher.verify("WrongPass1!", first.account().passwordHash()));
        assertNotEquals(first.account().passwordHash(), second.account().passwordHash());
        assertEquals(2, registry.count());
        assertEquals(first.account(), registry.findByUsername("θάνος").orElseThrow());
    }

    @Test
    void rejectsDuplicateUsernamesRegardlessOfCaseAndOuterSpaces() {
        AccountRegistry registry = new AccountRegistry();
        assertTrue(registry.register("Thanos", "Password123").created());

        for (String duplicate : List.of("Thanos", "thanos", "THANOS", "  thanos  ")) {
            var result = registry.register(duplicate, "AnotherPass123");
            assertFalse(result.created());
            assertEquals(AccountRegistry.RegistrationError.DUPLICATE_USERNAME, result.error());
        }
        assertEquals(1, registry.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ab", "a b", "user\nname", "user\tname", "aaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsInvalidUsernames(String username) {
        AccountRegistry registry = new AccountRegistry();

        var result = registry.register(username, "Password123");

        assertFalse(result.created());
        assertEquals(AccountRegistry.RegistrationError.INVALID_USERNAME, result.error());
        assertEquals(0, registry.count());
    }

    @Test
    void rejectsNullUsernameOrPassword() {
        AccountRegistry registry = new AccountRegistry();

        assertEquals(AccountRegistry.RegistrationError.INVALID_USERNAME,
                registry.register(null, "Password123").error());
        assertEquals(AccountRegistry.RegistrationError.INVALID_PASSWORD,
                registry.register("thanos", null).error());
        assertEquals(0, registry.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "        ", "short7", "pass\nword", "pass\t1234"})
    void rejectsInvalidPasswords(String password) {
        AccountRegistry registry = new AccountRegistry();

        var result = registry.register("thanos", password);

        assertFalse(result.created());
        assertEquals(AccountRegistry.RegistrationError.INVALID_PASSWORD, result.error());
        assertEquals(0, registry.count());
    }

    @Test
    void rejectsPasswordExceedingMaxLength() {
        AccountRegistry registry = new AccountRegistry();

        var result = registry.register("thanos", "a".repeat(AccountRegistry.MAX_PASSWORD_LENGTH + 1));

        assertFalse(result.created());
        assertEquals(AccountRegistry.RegistrationError.INVALID_PASSWORD, result.error());
    }

    @Test
    void rejectsMalformedStoredHashesWhenVerifying() {
        assertFalse(PasswordHasher.verify("Password123", null));
        assertFalse(PasswordHasher.verify("Password123", "plain"));
        assertFalse(PasswordHasher.verify("Password123", "pbkdf2-sha256$0$aa$bb"));
        assertFalse(PasswordHasher.verify("Password123", "pbkdf2-sha256$10$not-base64$bb"));
        assertFalse(PasswordHasher.verify(null, PasswordHasher.hash("Password123")));
    }

    @Test
    void allowsOnlyOneRegistrationUnderConcurrentRequestsForSameUsername() throws Exception {
        AccountRegistry registry = new AccountRegistry();
        int threads = 4;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(threads)) {
            List<Future<AccountRegistry.RegistrationResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String candidate = i % 2 == 0 ? "Keeper" : "keeper";
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return registry.register(candidate, "Password123");
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            long created = 0;
            long duplicates = 0;
            for (var future : futures) {
                var result = future.get(5, TimeUnit.SECONDS);
                if (result.created()) {
                    created++;
                } else if (result.error() == AccountRegistry.RegistrationError.DUPLICATE_USERNAME) {
                    duplicates++;
                }
            }
            assertEquals(1, created);
            assertEquals(threads - 1, duplicates);
            assertEquals(1, registry.count());
        }
    }
}
