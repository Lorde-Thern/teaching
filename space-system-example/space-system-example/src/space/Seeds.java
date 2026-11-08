package space;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * LESSON 04 and 05. Turning numbers into well-scrambled seeds.
 */
public final class Seeds {
    private Seeds() {}

    /**
     * The mod's mixer: scrambles a seed and two cell coordinates into one number, so that
     * neighbouring cells (5,7) and (5,8) get completely unrelated seeds. Each step (XOR,
     * multiply by an odd constant, XOR with a right-shifted copy) shuffles the bits.
     */
    public static long mix(long seed, int cx, int cz) {
        long h = seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }

    /**
     * LESSON 05's weakness, demonstrated: mix() can be RUN BACKWARDS. Every step has an inverse:
     *  - "h ^= h >>> 33" is undone by doing the same thing again (the top 33 bits never change);
     *  - multiplying by an odd number is undone by multiplying by its inverse modulo 2^64;
     *  - XOR with a known constant is undone by XOR-ing it again.
     * So anyone who knows the galaxy seed and the constants can recover the world seed.
     */
    public static long unmix(long mixed, int cx, int cz) {
        long h = mixed;
        h ^= h >>> 33;
        h *= inverse(0xC4CEB9FE1A85EC53L);
        h ^= h >>> 33;
        h *= inverse(0xFF51AFD7ED558CCDL);
        h ^= h >>> 33;
        return h ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
    }

    /** The number x with a*x = 1 (mod 2^64), for odd a (Newton's method: each step doubles the correct bits). */
    static long inverse(long a) {
        long x = a;
        for (int i = 0; i < 5; i++) x *= 2 - a * x;
        return x;
    }

    /**
     * The fix: a ONE-WAY hash. SHA-256 is designed so that knowing the output tells you nothing
     * useful about the input. The galaxy still comes from a seed; it just can't be traced back.
     */
    public static long oneWay(long worldSeed) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(ByteBuffer.allocate(16).putLong(worldSeed).putLong(0x6A1AC7L).array());
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
