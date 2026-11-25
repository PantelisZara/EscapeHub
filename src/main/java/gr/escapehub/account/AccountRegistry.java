package gr.escapehub.account;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Μητρώο λογαριασμών στη μνήμη με έλεγχο εγκυρότητας και μοναδικότητας ονόματος.
 */
public final class AccountRegistry {
    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MAX_USERNAME_LENGTH = 24;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private final Map<String, Account> accounts = new HashMap<>();

    public synchronized RegistrationResult register(String username, String password) {
        if (!isValidUsername(username)) {
            return new RegistrationResult(null, RegistrationError.INVALID_USERNAME);
        }
        if (!isValidPassword(password)) {
            return new RegistrationResult(null, RegistrationError.INVALID_PASSWORD);
        }
        String normalized = username.strip();
        String key = normalizeKey(normalized);
        if (accounts.containsKey(key)) {
            return new RegistrationResult(null, RegistrationError.DUPLICATE_USERNAME);
        }
        Account account = new Account(normalized, PasswordHasher.hash(password), Instant.now());
        accounts.put(key, account);
        return new RegistrationResult(account, null);
    }

    public synchronized Optional<Account> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        String key = normalizeKey(username.strip());
        return Optional.ofNullable(accounts.get(key));
    }

    public synchronized int count() {
        return accounts.size();
    }

    private static boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        String trimmed = username.strip();
        int length = trimmed.length();
        return length >= MIN_USERNAME_LENGTH && length <= MAX_USERNAME_LENGTH
                && trimmed.codePoints().noneMatch(cp -> Character.isWhitespace(cp) || Character.isISOControl(cp));
    }

    private static boolean isValidPassword(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        int length = password.length();
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
