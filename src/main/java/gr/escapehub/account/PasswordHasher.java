package gr.escapehub.account;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Υπολογισμός και επαλήθευση password hash με PBKDF2-HMAC-SHA256 και τυχαίο salt.
 */
public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password must not be null");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + encoder.encodeToString(salt)
                + "$" + encoder.encodeToString(derived);
    }

    public static boolean verify(String password, String encodedHash) {
        if (password == null) {
            return false;
        }
        ParsedHash hash = parse(encodedHash);
        return hash != null && MessageDigest.isEqual(hash.key(), derive(password, hash.salt(), hash.iterations()));
    }

    static boolean isEncodedHash(String encodedHash) {
        return parse(encodedHash) != null;
    }

    private static ParsedHash parse(String encodedHash) {
        if (encodedHash == null) {
            return null;
        }
        String[] parts = encodedHash.split("\\$", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return null;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations <= 0 || iterations > ITERATIONS) {
                return null;
            }
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[2]);
            byte[] key = decoder.decode(parts[3]);
            if (salt.length == SALT_BYTES && key.length == KEY_BITS / Byte.SIZE) {
                return new ParsedHash(iterations, salt, key);
            }
        } catch (IllegalArgumentException invalidFormat) {
            return null;
        }
        return null;
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException failure) {
            throw new IllegalStateException("PBKDF2-HMAC-SHA256 unavailable", failure);
        } finally {
            spec.clearPassword();
        }
    }

    private record ParsedHash(int iterations, byte[] salt, byte[] key) { }
}
