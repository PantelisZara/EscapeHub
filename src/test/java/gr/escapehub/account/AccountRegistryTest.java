package gr.escapehub.account;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountRegistryTest {
    @TempDir
    Path temporaryDirectory;
    private Path file;
    private AccountRegistry registry;

    @BeforeEach
    void openRegistry() throws IOException {
        file = temporaryDirectory.resolve("accounts.tsv");
        registry = new AccountRegistry(file);
    }

    @Test
    void registersAccountWithSaltedPasswordHash() throws IOException {

        var first = registry.register("Thanos", "Secret123!");
        var second = registry.register("Manos", "Secret123!");

        assertTrue(first.created());
        assertNull(first.error());
        assertEquals("Thanos", first.account().username());
        assertNotEquals("Secret123!", first.account().passwordHash());
        assertTrue(first.account().passwordHash().startsWith("pbkdf2-sha256$600000$"));
        assertFalse(first.account().toString().contains(first.account().passwordHash()));
        assertTrue(PasswordHasher.verify("Secret123!", first.account().passwordHash()));
        assertFalse(PasswordHasher.verify("WrongPass1!", first.account().passwordHash()));
        assertNotEquals(first.account().passwordHash(), second.account().passwordHash());
        assertEquals(2, registry.count());
        assertEquals(first.account(), registry.findByUsername("thanos").orElseThrow());
    }

    @Test
    void rejectsDuplicateUsernamesRegardlessOfCase() throws IOException {
        assertTrue(registry.register("Thanos", "Password123").created());

        for (String duplicate : List.of("Thanos", "thanos", "THANOS")) {
            var result = registry.register(duplicate, "AnotherPass123");
            assertFalse(result.created());
            assertEquals(AccountRegistry.RegistrationError.DUPLICATE_USERNAME, result.error());
        }
        assertEquals(1, registry.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ab", "a b", " Thanos ", "Θάνος", "user\nname", "user\tname",
            "aaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsInvalidUsernames(String username) throws IOException {

        var result = registry.register(username, "Password123");

        assertFalse(result.created());
        assertEquals(AccountRegistry.RegistrationError.INVALID_USERNAME, result.error());
        assertEquals(0, registry.count());
    }

    @Test
    void rejectsNullUsernameOrPassword() throws IOException {

        assertEquals(AccountRegistry.RegistrationError.INVALID_USERNAME,
                registry.register(null, "Password123").error());
        assertEquals(AccountRegistry.RegistrationError.INVALID_PASSWORD,
                registry.register("thanos", null).error());
        assertEquals(0, registry.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "        ", "short7", "pass\nword", "pass\t1234"})
    void rejectsInvalidPasswords(String password) throws IOException {

        var result = registry.register("thanos", password);

        assertFalse(result.created());
        assertEquals(AccountRegistry.RegistrationError.INVALID_PASSWORD, result.error());
        assertEquals(0, registry.count());
    }

    @Test
    void rejectsPasswordExceedingMaxLength() throws IOException {

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
    void reloadsAccountsAndRejectsDuplicatesAfterRestart() throws IOException {
        String password = "Κωδικός!123";
        Account account = registry.register("Keeper_1", password).account();

        String contents = Files.readString(file);
        assertFalse(contents.contains(password));
        assertTrue(contents.contains(account.passwordHash()));

        AccountRegistry reopened = new AccountRegistry(file);
        assertEquals(account, reopened.findByUsername("KEEPER_1").orElseThrow());
        assertTrue(PasswordHasher.verify(password, account.passwordHash()));
        assertEquals(AccountRegistry.RegistrationError.DUPLICATE_USERNAME,
                reopened.register("keeper_1", "OtherPass123").error());
    }

    @Test
    void keepsRegistryUnchangedAfterFailedWrite() throws IOException {
        Files.createDirectory(file);

        assertThrows(IOException.class, () -> registry.register("Keeper", "Password123"));
        assertEquals(0, registry.count());
        try (var entries = Files.list(temporaryDirectory)) {
            assertEquals(List.of(file), entries.toList());
        }

        Files.delete(file);
        assertTrue(registry.register("Keeper", "Password123").created());
        assertEquals(1, new AccountRegistry(file).count());
    }

    @Test
    void refusesMalformedAccountFile() throws IOException {
        Files.writeString(file, "Keeper\tinvalid\t2026-11-25T21:00:00Z");
        assertThrows(IOException.class, () -> new AccountRegistry(file));
    }

    @Test
    void allowsOnlyOneRegistrationUnderConcurrentRequestsForSameUsername() throws Exception {
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
