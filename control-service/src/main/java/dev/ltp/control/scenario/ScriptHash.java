package dev.ltp.control.scenario;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of a k6 script, as stored in {@code scenario_versions.sha256}: lower-case hex of the UTF-8
 * bytes, exactly as saved (no trimming or line-ending changes). US-02 compares it to skip saving an
 * identical script.
 */
public final class ScriptHash {

    private ScriptHash() {}

    public static String sha256Hex(String script) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(script.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime must support SHA-256", e);
        }
    }

    /** Size of the script in UTF-8 bytes, the unit of the 1 MB limit (US-01). */
    public static int utf8Length(String script) {
        return script.getBytes(StandardCharsets.UTF_8).length;
    }
}
