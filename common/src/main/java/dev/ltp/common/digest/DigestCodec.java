package dev.ltp.common.digest;

import com.tdunning.math.stats.MergingDigest;
import com.tdunning.math.stats.TDigest;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Collection;

/**
 * Creates, serializes and merges t-digests.
 *
 * <p>Rule: percentiles are never averaged. Per-worker digests are merged and the cluster percentile
 * is read from the merged digest (NFR-03, KPI-2).
 */
public final class DigestCodec {

    /** Compression used everywhere (NFR-03 suggests 100–200). */
    public static final double COMPRESSION = 100;

    private DigestCodec() {}

    public static TDigest newDigest() {
        return TDigest.createMergingDigest(COMPRESSION);
    }

    public static byte[] toBytes(TDigest digest) {
        ByteBuffer buffer = ByteBuffer.allocate(digest.smallByteSize());
        digest.asSmallBytes(buffer);
        return buffer.array();
    }

    public static TDigest fromBytes(byte[] bytes) {
        return MergingDigest.fromBytes(ByteBuffer.wrap(bytes));
    }

    public static String toBase64(TDigest digest) {
        return Base64.getEncoder().encodeToString(toBytes(digest));
    }

    public static TDigest fromBase64(String base64) {
        return fromBytes(Base64.getDecoder().decode(base64));
    }

    /** Merges digests into a new digest; inputs are not modified. */
    public static TDigest merge(Collection<? extends TDigest> digests) {
        TDigest merged = newDigest();
        for (TDigest digest : digests) {
            merged.add(digest);
        }
        return merged;
    }
}
