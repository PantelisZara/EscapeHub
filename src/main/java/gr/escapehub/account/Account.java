package gr.escapehub.account;

import java.time.Instant;
import java.util.Objects;

/**
 * Αποθηκευμένος λογαριασμός παίκτη.
 */
public record Account(String username, String passwordHash, Instant createdAt) {
    public Account {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    @Override
    public String toString() {
        return "Account[username=" + username + ", createdAt=" + createdAt + "]";
    }
}
