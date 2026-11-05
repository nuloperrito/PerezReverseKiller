package com.perez.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {

    private static final int BUFFER_SIZE = 65536; // 64 KB buffer for streaming
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private HashUtil() {
        // Prevent instantiation
    }

    // ==========================================
    // Public API Methods
    // ==========================================

    public static String crc32(File file) throws IOException {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = is.read(buffer)) != -1) {
                crc.update(buffer, 0, read);
            }
        }
        return toHex32(crc.getValue());
    }

    public static String crc64(File file) throws IOException {
        Crc64 crc = new Crc64();
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = is.read(buffer)) != -1) {
                crc.update(buffer, 0, read);
            }
        }
        return toHex64(crc.getValue());
    }

    public static String md5(byte[] bytes) {
        if (bytes == null) return "null";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "null";
        }
    }

    public static String md5(File file) throws IOException {
        return digestStandard(file, "MD5");
    }

    public static String sha1(File file) throws IOException {
        return digestStandard(file, "SHA-1");
    }

    public static String sha256(File file) throws IOException {
        return digestStandard(file, "SHA-256");
    }

    public static String sha512(File file) throws IOException {
        return digestStandard(file, "SHA-512");
    }

    public static String sha3_256(File file) throws IOException {
        Sha3_256 sha3 = new Sha3_256();
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = is.read(buffer)) != -1) {
                sha3.update(buffer, 0, read);
            }
        }
        return bytesToHex(sha3.digest());
    }

    public static String blake2sp(File file) throws IOException {
        Blake2sp blake2sp = new Blake2sp();
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = is.read(buffer)) != -1) {
                blake2sp.update(buffer, 0, read);
            }
        }
        return bytesToHex(blake2sp.digest());
    }

    public static String xxh64(File file) throws IOException {
        Xxh64 xxh64 = new Xxh64(0L);
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = is.read(buffer)) != -1) {
                xxh64.update(buffer, 0, read);
            }
        }
        return toHex64(xxh64.digest());
    }

    // ==========================================
    // Internal Streaming Digest Helpers
    // ==========================================

    private static String digestStandard(File file, String algorithm) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            try (InputStream is = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithm not supported: " + algorithm, e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hex);
    }

    private static String toHex32(long value) {
        char[] hex = new char[8];
        for (int i = 7; i >= 0; i--) {
            hex[i] = HEX_CHARS[(int) (value & 0x0F)];
            value >>>= 4;
        }
        return new String(hex);
    }

    private static String toHex64(long value) {
        char[] hex = new char[16];
        for (int i = 15; i >= 0; i--) {
            hex[i] = HEX_CHARS[(int) (value & 0x0F)];
            value >>>= 4;
        }
        return new String(hex);
    }

    // ==========================================
    // Standalone Algorithm Implementations
    // ==========================================

    /**
     * Standard CRC64 implementation using ECMA-182 polynomial (reflected).
     */
    private static final class Crc64 {
        private static final long POLY = 0xC96C5795D7870F42L;
        private static final long[] TABLE = new long[256];

        static {
            for (int i = 0; i < 256; i++) {
                long crc = i;
                for (int j = 0; j < 8; j++) {
                    if ((crc & 1L) != 0) {
                        crc = (crc >>> 1) ^ POLY;
                    } else {
                        crc >>>= 1;
                    }
                }
                TABLE[i] = crc;
            }
        }

        private long crc = 0xFFFFFFFFFFFFFFFFL;

        void update(byte[] b, int off, int len) {
            for (int i = 0; i < len; i++) {
                int idx = ((int) crc ^ b[off + i]) & 0xFF;
                crc = TABLE[idx] ^ (crc >>> 8);
            }
        }

        long getValue() {
            return crc ^ 0xFFFFFFFFFFFFFFFFL;
        }
    }

    /**
     * Standalone SHA3-256 implementation adhering to FIPS 202.
     */
    private static final class Sha3_256 {
        private static final int RATE_BYTES = 136; // (1600 - 512) / 8
        private static final long[] RC = {
                0x0000000000000001L, 0x0000000000008082L, 0x800000000000808aL, 0x8000000080008000L,
                0x000000000000808bL, 0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L,
                0x000000000000008aL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000aL,
                0x000000008000808bL, 0x800000000000008bL, 0x8000000000008089L, 0x8000000000008003L,
                0x8000000000008002L, 0x8000000000000080L, 0x000000000000800aL, 0x800000008000000aL,
                0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
        };

        private static final int[][] RHO = {
                { 0, 36,  3, 41, 18 },
                { 1, 44, 10, 45,  2 },
                { 62,  6, 43, 15, 61 },
                { 28, 55, 25, 21, 56 },
                { 27, 20, 39,  8, 14 }
        };

        private final long[][] state = new long[5][5];
        private final byte[] buffer = new byte[RATE_BYTES];
        private int bufLen = 0;

        private final long[] c = new long[5];
        private final long[] d = new long[5];
        private final long[][] b = new long[5][5];

        void update(byte[] in, int off, int len) {
            while (len > 0) {
                if (bufLen == RATE_BYTES) {
                    absorb(buffer, 0);
                    bufLen = 0;
                }
                int toCopy = Math.min(len, RATE_BYTES - bufLen);
                System.arraycopy(in, off, buffer, bufLen, toCopy);
                bufLen += toCopy;
                off += toCopy;
                len -= toCopy;
            }
        }

        byte[] digest() {
            if (bufLen == RATE_BYTES) {
                absorb(buffer, 0);
                bufLen = 0;
            }
            // SHA-3 domain separation pad: 0x06 ... 0x80
            buffer[bufLen++] = 0x06;
            while (bufLen < RATE_BYTES) {
                buffer[bufLen++] = 0x00;
            }
            buffer[RATE_BYTES - 1] |= (byte) 0x80;
            absorb(buffer, 0);

            byte[] out = new byte[32];
            for (int i = 0; i < 4; i++) {
                long lane = state[i][0];
                for (int j = 0; j < 8; j++) {
                    out[i * 8 + j] = (byte) (lane >>> (8 * j));
                }
            }
            return out;
        }

        private void absorb(byte[] block, int off) {
            for (int i = 0; i < 17; i++) {
                int idx = off + i * 8;
                long lane = ((long) (block[idx] & 0xFF))
                        | (((long) (block[idx + 1] & 0xFF)) << 8)
                        | (((long) (block[idx + 2] & 0xFF)) << 16)
                        | (((long) (block[idx + 3] & 0xFF)) << 24)
                        | (((long) (block[idx + 4] & 0xFF)) << 32)
                        | (((long) (block[idx + 5] & 0xFF)) << 40)
                        | (((long) (block[idx + 6] & 0xFF)) << 48)
                        | (((long) (block[idx + 7] & 0xFF)) << 56);
                state[i % 5][i / 5] ^= lane;
            }
            keccakF();
        }

        private void keccakF() {
            for (int round = 0; round < 24; round++) {
                // Theta
                for (int x = 0; x < 5; x++) {
                    c[x] = state[x][0] ^ state[x][1] ^ state[x][2] ^ state[x][3] ^ state[x][4];
                }
                for (int x = 0; x < 5; x++) {
                    d[x] = c[(x + 4) % 5] ^ Long.rotateLeft(c[(x + 1) % 5], 1);
                }
                for (int x = 0; x < 5; x++) {
                    for (int y = 0; y < 5; y++) {
                        state[x][y] ^= d[x];
                    }
                }

                // Rho & Pi
                for (int x = 0; x < 5; x++) {
                    for (int y = 0; y < 5; y++) {
                        b[y][(2 * x + 3 * y) % 5] = Long.rotateLeft(state[x][y], RHO[x][y]);
                    }
                }

                // Chi
                for (int x = 0; x < 5; x++) {
                    for (int y = 0; y < 5; y++) {
                        state[x][y] = b[x][y] ^ ((~b[(x + 1) % 5][y]) & b[(x + 2) % 5][y]);
                    }
                }

                // Iota
                state[0][0] ^= RC[round];
            }
        }
    }

    /**
     * Standalone BLAKE2s single-node implementation for tree hashing.
     */
    private static final class Blake2s {
        private static final int[] IV = {
                0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
                0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19
        };

        private static final byte[][] SIGMA = {
                {  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15 },
                { 14, 10,  4,  8,  9, 15, 13,  6,  1, 12,  0,  2, 11,  7,  5,  3 },
                { 11,  8, 12,  0,  5,  2, 15, 13, 10, 14,  3,  6,  7,  1,  9,  4 },
                {  7,  9,  3,  1, 13, 12, 11, 14,  2,  6,  5, 10,  4,  0, 15,  8 },
                {  9,  0,  5,  7,  2,  4, 10, 15, 14,  1, 11, 12,  6,  8,  3, 13 },
                {  2, 12,  6, 10,  0, 11,  8,  3,  4, 13,  7,  5, 15, 14,  1,  9 },
                { 12,  5,  1, 15, 14, 13,  4, 10,  0,  7,  6,  3,  9,  2,  8, 11 },
                { 13, 11,  7, 14, 12,  1,  3,  9,  5,  0, 15,  4,  8,  6,  2, 10 },
                {  6, 15, 14,  9, 11,  3,  0,  8, 12,  2, 13,  7,  1,  4, 10,  5 },
                { 10,  2,  8,  4,  7,  6,  1,  5, 15, 11,  9, 14,  3, 12, 13,  0 }
        };

        private final int[] h = new int[8];
        private final byte[] buf = new byte[64];
        private int buflen = 0;
        private long totalBytes = 0;
        private final boolean isLastNode;

        private final int[] m = new int[16];
        private final int[] v = new int[16];

        Blake2s(int nodeOffset, int nodeDepth, boolean isLastNode) {
            this.isLastNode = isLastNode;
            // digest_length = 32 (0x20), key_length = 0, fanout = 8 (0x08), depth = 2 (0x02)
            int p0 = 0x02080020;
            int p1 = 0; // leaf_length = 0
            int p2 = nodeOffset;
            int p3 = (nodeDepth << 16) | (32 << 24); // inner_length = 32 (0x20)

            h[0] = IV[0] ^ p0;
            h[1] = IV[1] ^ p1;
            h[2] = IV[2] ^ p2;
            h[3] = IV[3] ^ p3;
            h[4] = IV[4];
            h[5] = IV[5];
            h[6] = IV[6];
            h[7] = IV[7];
        }

        void update(byte[] in, int off, int len) {
            while (len > 0) {
                if (buflen == 64) {
                    totalBytes += 64;
                    compress(buf, 0, totalBytes, false);
                    buflen = 0;
                }
                int toCopy = Math.min(len, 64 - buflen);
                System.arraycopy(in, off, buf, buflen, toCopy);
                buflen += toCopy;
                off += toCopy;
                len -= toCopy;
            }
        }

        byte[] digest() {
            totalBytes += buflen;
            while (buflen < 64) {
                buf[buflen++] = 0;
            }
            compress(buf, 0, totalBytes, true);

            byte[] out = new byte[32];
            for (int i = 0; i < 8; i++) {
                out[i * 4] = (byte) (h[i]);
                out[i * 4 + 1] = (byte) (h[i] >>> 8);
                out[i * 4 + 2] = (byte) (h[i] >>> 16);
                out[i * 4 + 3] = (byte) (h[i] >>> 24);
            }
            return out;
        }

        private void compress(byte[] block, int off, long t, boolean isLastBlock) {
            for (int i = 0; i < 16; i++) {
                int idx = off + i * 4;
                m[i] = (block[idx] & 0xFF)
                        | ((block[idx + 1] & 0xFF) << 8)
                        | ((block[idx + 2] & 0xFF) << 16)
                        | ((block[idx + 3] & 0xFF) << 24);
            }

            System.arraycopy(h, 0, v, 0, 8);
            System.arraycopy(IV, 0, v, 8, 8);
            v[12] ^= (int) t;
            v[13] ^= (int) (t >>> 32);
            if (isLastBlock) {
                v[14] ^= 0xFFFFFFFF; // f[0] = -1 (last block)
                if (isLastNode) {
                    v[15] ^= 0xFFFFFFFF; // f[1] = -1 (last node in tree layer)
                }
            }

            for (int r = 0; r < 10; r++) {
                byte[] s = SIGMA[r];
                g(0, 4, 8, 12, m[s[0]], m[s[1]]);
                g(1, 5, 9, 13, m[s[2]], m[s[3]]);
                g(2, 6, 10, 14, m[s[4]], m[s[5]]);
                g(3, 7, 11, 15, m[s[6]], m[s[7]]);
                g(0, 5, 10, 15, m[s[8]], m[s[9]]);
                g(1, 6, 11, 12, m[s[10]], m[s[11]]);
                g(2, 7, 8, 13, m[s[12]], m[s[13]]);
                g(3, 4, 9, 14, m[s[14]], m[s[15]]);
            }

            for (int i = 0; i < 8; i++) {
                h[i] ^= v[i] ^ v[i + 8];
            }
        }

        private void g(int a, int b, int c, int d, int x, int y) {
            v[a] = v[a] + v[b] + x;
            v[d] = Integer.rotateRight(v[d] ^ v[a], 16);
            v[c] = v[c] + v[d];
            v[b] = Integer.rotateRight(v[b] ^ v[c], 12);
            v[a] = v[a] + v[b] + y;
            v[d] = Integer.rotateRight(v[d] ^ v[a], 8);
            v[c] = v[c] + v[d];
            v[b] = Integer.rotateRight(v[b] ^ v[c], 7);
        }
    }

    /**
     * Parallel BLAKE2sp tree implementation (8 parallel leaf instances + root node).
     */
    private static final class Blake2sp {
        private final Blake2s[] leaves = new Blake2s[8];
        private final byte[] buf = new byte[512];
        private int buflen = 0;

        Blake2sp() {
            for (int i = 0; i < 8; i++) {
                // Only the 8th leaf (index 7) has isLastNode = true
                leaves[i] = new Blake2s(i, 0, i == 7);
            }
        }

        void update(byte[] in, int off, int len) {
            if (buflen > 0 && buflen + len >= 512) {
                int fill = 512 - buflen;
                System.arraycopy(in, off, buf, buflen, fill);
                for (int i = 0; i < 8; i++) {
                    leaves[i].update(buf, i * 64, 64);
                }
                off += fill;
                len -= fill;
                buflen = 0;
            }

            while (len >= 512) {
                for (int i = 0; i < 8; i++) {
                    leaves[i].update(in, off + i * 64, 64);
                }
                off += 512;
                len -= 512;
            }

            if (len > 0) {
                System.arraycopy(in, off, buf, buflen, len);
                buflen += len;
            }
        }

        byte[] digest() {
            for (int i = 0; i < 8; i++) {
                if (buflen > i * 64) {
                    int left = buflen - i * 64;
                    if (left > 64) {
                        left = 64;
                    }
                    leaves[i].update(buf, i * 64, left);
                }
            }

            // Root node is at nodeDepth = 1 and is the last node of its layer (isLastNode = true)
            Blake2s root = new Blake2s(0, 1, true);
            for (int i = 0; i < 8; i++) {
                byte[] leafHash = leaves[i].digest();
                root.update(leafHash, 0, 32);
            }
            return root.digest();
        }
    }

    /**
     * Standalone xxHash64 implementation adhering to Cyan4973's specification.
     */
    private static final class Xxh64 {
        private static final long PRIME64_1 = 0x9E3779B185EBCA87L;
        private static final long PRIME64_2 = 0xC2B2AE3D27D4EB4FL;
        private static final long PRIME64_3 = 0x165667B19E3779F9L;
        private static final long PRIME64_4 = 0x85EBCA77C2B2AE63L;
        private static final long PRIME64_5 = 0x27D4EB2F165667C5L;

        private long v1;
        private long v2;
        private long v3;
        private long v4;
        private long totalLen;
        private final byte[] mem = new byte[32];
        private int memSize = 0;

        Xxh64(long seed) {
            v1 = seed + PRIME64_1 + PRIME64_2;
            v2 = seed + PRIME64_2;
            v3 = seed;
            v4 = seed - PRIME64_1;
            totalLen = 0;
            memSize = 0;
        }

        void update(byte[] in, int off, int len) {
            totalLen += len;
            if (memSize + len < 32) {
                System.arraycopy(in, off, mem, memSize, len);
                memSize += len;
                return;
            }

            if (memSize > 0) {
                int fill = 32 - memSize;
                System.arraycopy(in, off, mem, memSize, fill);
                v1 = round(v1, read64(mem, 0));
                v2 = round(v2, read64(mem, 8));
                v3 = round(v3, read64(mem, 16));
                v4 = round(v4, read64(mem, 24));
                off += fill;
                len -= fill;
                memSize = 0;
            }

            while (len >= 32) {
                v1 = round(v1, read64(in, off));
                v2 = round(v2, read64(in, off + 8));
                v3 = round(v3, read64(in, off + 16));
                v4 = round(v4, read64(in, off + 24));
                off += 32;
                len -= 32;
            }

            if (len > 0) {
                System.arraycopy(in, off, mem, 0, len);
                memSize = len;
            }
        }

        long digest() {
            long h64;
            if (totalLen >= 32) {
                h64 = Long.rotateLeft(v1, 1) + Long.rotateLeft(v2, 7)
                        + Long.rotateLeft(v3, 12) + Long.rotateLeft(v4, 18);
                h64 = mergeRound(h64, v1);
                h64 = mergeRound(h64, v2);
                h64 = mergeRound(h64, v3);
                h64 = mergeRound(h64, v4);
            } else {
                h64 = v3 + PRIME64_5;
            }

            h64 += totalLen;

            int p = 0;
            while (p + 8 <= memSize) {
                long k1 = round(0, read64(mem, p));
                h64 = Long.rotateLeft(h64 ^ k1, 27) * PRIME64_1 + PRIME64_4;
                p += 8;
            }

            if (p + 4 <= memSize) {
                long k1 = (read32(mem, p) & 0xFFFFFFFFL) * PRIME64_1;
                h64 = Long.rotateLeft(h64 ^ k1, 23) * PRIME64_2 + PRIME64_3;
                p += 4;
            }

            while (p < memSize) {
                long k1 = (mem[p] & 0xFFL) * PRIME64_5;
                h64 = Long.rotateLeft(h64 ^ k1, 11) * PRIME64_1;
                p++;
            }

            h64 ^= h64 >>> 33;
            h64 *= PRIME64_2;
            h64 ^= h64 >>> 29;
            h64 *= PRIME64_3;
            h64 ^= h64 >>> 32;

            return h64;
        }

        private static long round(long acc, long val) {
            acc += val * PRIME64_2;
            acc = Long.rotateLeft(acc, 31);
            acc *= PRIME64_1;
            return acc;
        }

        private static long mergeRound(long acc, long val) {
            long v = round(0, val);
            acc ^= v;
            acc = acc * PRIME64_1 + PRIME64_4;
            return acc;
        }

        private static long read64(byte[] b, int off) {
            return ((long) (b[off] & 0xFF))
                    | (((long) (b[off + 1] & 0xFF)) << 8)
                    | (((long) (b[off + 2] & 0xFF)) << 16)
                    | (((long) (b[off + 3] & 0xFF)) << 24)
                    | (((long) (b[off + 4] & 0xFF)) << 32)
                    | (((long) (b[off + 5] & 0xFF)) << 40)
                    | (((long) (b[off + 6] & 0xFF)) << 48)
                    | (((long) (b[off + 7] & 0xFF)) << 56);
        }

        private static int read32(byte[] b, int off) {
            return (b[off] & 0xFF)
                    | ((b[off + 1] & 0xFF) << 8)
                    | ((b[off + 2] & 0xFF) << 16)
                    | ((b[off + 3] & 0xFF) << 24);
        }
    }
}