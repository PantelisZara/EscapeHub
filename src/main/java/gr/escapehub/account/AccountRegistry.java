package gr.escapehub.account;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Μητρώο λογαριασμών με ένα αρχείο ανά διακομιστή.
 */
public final class AccountRegistry {
    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MAX_USERNAME_LENGTH = 24;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_-]{"
            + MIN_USERNAME_LENGTH + "," + MAX_USERNAME_LENGTH + "}");
    private final Path file;
    private final Map<String, Account> accounts = new LinkedHashMap<>();

    public AccountRegistry(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        load();
    }

    public RegistrationResult register(String username, String password) throws IOException {
        if (!isValidUsername(username)) {
            return new RegistrationResult(null, RegistrationError.INVALID_USERNAME);
        }
        if (!isValidPassword(password)) {
            return new RegistrationResult(null, RegistrationError.INVALID_PASSWORD);
        }
        String key = normalizeKey(username);
        synchronized (this) {
            if (accounts.containsKey(key)) {
                return new RegistrationResult(null, RegistrationError.DUPLICATE_USERNAME);
            }
        }

        Account account = new Account(username, PasswordHasher.hash(password), Instant.now());
        synchronized (this) {
            if (accounts.containsKey(key)) {
                return new RegistrationResult(null, RegistrationError.DUPLICATE_USERNAME);
            }
            saveWith(account);
            accounts.put(key, account);
            return new RegistrationResult(account, null);
        }
    }

    public synchronized Optional<Account> findByUsername(String username) {
        if (!isValidUsername(username)) {
            return Optional.empty();
        }
        return Optional.ofNullable(accounts.get(normalizeKey(username)));
    }

    public synchronized int count() {
        return accounts.size();
    }

    private void load() throws IOException {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (NoSuchFileException missing) {
            return;
        }
        for (String line : lines) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 3 || !isValidUsername(fields[0]) || !PasswordHasher.isEncodedHash(fields[1])) {
                throw new IOException("Invalid account file");
            }
            Account account;
            try {
                account = new Account(fields[0], fields[1], Instant.parse(fields[2]));
            } catch (DateTimeParseException malformed) {
                throw new IOException("Invalid account file");
            }
            if (accounts.putIfAbsent(normalizeKey(account.username()), account) != null) {
                throw new IOException("Duplicate username in account file");
            }
        }
    }

    private void saveWith(Account account) throws IOException {
        List<String> lines = new ArrayList<>(accounts.size() + 1);
        for (Account stored : accounts.values()) {
            lines.add(encode(stored));
        }
        lines.add(encode(account));
        Path directory = file.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".accounts-", ".tmp");
        try {
            Files.write(temporary, lines);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            // Publish the complete file before making the account visible in memory.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException saveFailure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                saveFailure.addSuppressed(cleanupFailure);
            }
            throw saveFailure;
        }
    }

    private static String encode(Account account) {
        return account.username() + "\t" + account.passwordHash() + "\t" + account.createdAt();
    }

    private static boolean isValidUsername(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    private static boolean isValidPassword(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        int length = password.codePointCount(0, password.length());
        return length >= MIN_PASSWORD_LENGTH && length <= MAX_PASSWORD_LENGTH
                && password.codePoints().noneMatch(Character::isISOControl);
    }

    private static String normalizeKey(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public enum RegistrationError {
        INVALID_USERNAME,
        INVALID_PASSWORD,
        DUPLICATE_USERNAME
    }

    public record RegistrationResult(Account account, RegistrationError error) {
        public boolean created() {
            return account != null;
        }
    }
}
