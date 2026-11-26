package dev.ltp.control.worker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Per-worker secrets (US-06). A secret is 32 random bytes, so it cannot be guessed; the database keeps
 * only its SHA-256. A slow password hash (bcrypt) would add nothing for such a value, but would cost
 * about 50 ms on every heartbeat and metrics batch (NFR-02).
 */
public final class WorkerSecrets {

    private static final SecureRandom RANDOM = new SecureRandom();

    private WorkerSecrets() {}

    /** A new secret, URL-safe Base64 (43 characters). */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lower-case hex SHA-256 of the UTF-8 bytes; what {@code workers.secret_hash} stores. */
    public static String hash(String secret) {
        return HexFormat.of().formatHex(sha256(secret));
    }

    /** Compares two secrets in constant time (also when their lengths differ). */
    public static boolean matches(String given, String expected) {
        return given != null && MessageDigest.isEqual(sha256(given), sha256(expected));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every JDK", e);
        }
    }
}
