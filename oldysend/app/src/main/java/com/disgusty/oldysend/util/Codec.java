package com.disgusty.oldysend.util;

import java.security.MessageDigest;
import java.security.SecureRandom;

/** Hex, Base64 and hashing; android.util.Base64 only exists since API 8. */
public final class Codec {
    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();
    private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Codec() {
    }

    public static String hexUpper(byte[] data) {
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            out[i * 2] = HEX_UPPER[(data[i] >> 4) & 0xF];
            out[i * 2 + 1] = HEX_UPPER[data[i] & 0xF];
        }
        return new String(out);
    }

    public static String hexLower(byte[] data) {
        return Text.lower(hexUpper(data));
    }

    public static String base64(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i + 2 < data.length) {
            int n = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8) | (data[i + 2] & 0xFF);
            sb.append(B64[(n >> 18) & 63]).append(B64[(n >> 12) & 63]).append(B64[(n >> 6) & 63]).append(B64[n & 63]);
            i += 3;
        }
        int rest = data.length - i;
        if (rest == 1) {
            int n = (data[i] & 0xFF) << 16;
            sb.append(B64[(n >> 18) & 63]).append(B64[(n >> 12) & 63]).append("==");
        } else if (rest == 2) {
            int n = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8);
            sb.append(B64[(n >> 18) & 63]).append(B64[(n >> 12) & 63]).append(B64[(n >> 6) & 63]).append('=');
        }
        return sb.toString();
    }

    public static byte[] base64Decode(String s) {
        int[] map = new int[128];
        for (int i = 0; i < map.length; i++) map[i] = -1;
        for (int i = 0; i < B64.length; i++) map[B64[i]] = i;
        map['-'] = 62;
        map['_'] = 63;
        byte[] out = new byte[s.length() * 3 / 4 + 3];
        int len = 0;
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 128 || map[c] < 0) continue;
            buffer = (buffer << 6) | map[c];
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[len++] = (byte) ((buffer >> bits) & 0xFF);
            }
        }
        byte[] result = new byte[len];
        System.arraycopy(out, 0, result, 0, len);
        return result;
    }

    public static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            return new Sha256();
        }
    }

    public static byte[] sha256(byte[] data) {
        MessageDigest md = sha256Digest();
        md.update(data);
        return md.digest();
    }

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    public static int randomInt(int bound) {
        return RANDOM.nextInt(bound);
    }

    /** Random alphanumeric string, used for HTTP-mode fingerprints and tokens. */
    public static String randomId() {
        return java.util.UUID.randomUUID().toString();
    }

    /** Pure Java SHA-256 for platforms whose provider lacks it. */
    static final class Sha256 extends MessageDigest {
        private static final int[] K = {
                0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
                0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
                0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
                0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
                0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
                0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
                0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
                0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

        private final int[] h = new int[8];
        private final byte[] block = new byte[64];
        private final int[] w = new int[64];
        private int blockLen;
        private long total;

        Sha256() {
            super("SHA-256");
            engineReset();
        }

        @Override
        protected void engineReset() {
            h[0] = 0x6a09e667; h[1] = 0xbb67ae85; h[2] = 0x3c6ef372; h[3] = 0xa54ff53a;
            h[4] = 0x510e527f; h[5] = 0x9b05688c; h[6] = 0x1f83d9ab; h[7] = 0x5be0cd19;
            blockLen = 0;
            total = 0;
        }

        @Override
        protected void engineUpdate(byte input) {
            block[blockLen++] = input;
            total++;
            if (blockLen == 64) {
                compress();
                blockLen = 0;
            }
        }

        @Override
        protected void engineUpdate(byte[] input, int offset, int len) {
            for (int i = 0; i < len; i++) engineUpdate(input[offset + i]);
        }

        @Override
        protected byte[] engineDigest() {
            long bits = total * 8;
            engineUpdate((byte) 0x80);
            while (blockLen != 56) engineUpdate((byte) 0);
            for (int i = 7; i >= 0; i--) engineUpdate((byte) (bits >>> (i * 8)));
            byte[] out = new byte[32];
            for (int i = 0; i < 8; i++) {
                out[i * 4] = (byte) (h[i] >>> 24);
                out[i * 4 + 1] = (byte) (h[i] >>> 16);
                out[i * 4 + 2] = (byte) (h[i] >>> 8);
                out[i * 4 + 3] = (byte) h[i];
            }
            engineReset();
            return out;
        }

        private void compress() {
            for (int i = 0; i < 16; i++) {
                w[i] = ((block[i * 4] & 0xFF) << 24) | ((block[i * 4 + 1] & 0xFF) << 16)
                        | ((block[i * 4 + 2] & 0xFF) << 8) | (block[i * 4 + 3] & 0xFF);
            }
            for (int i = 16; i < 64; i++) {
                int s0 = Integer.rotateRight(w[i - 15], 7) ^ Integer.rotateRight(w[i - 15], 18) ^ (w[i - 15] >>> 3);
                int s1 = Integer.rotateRight(w[i - 2], 17) ^ Integer.rotateRight(w[i - 2], 19) ^ (w[i - 2] >>> 10);
                w[i] = w[i - 16] + s0 + w[i - 7] + s1;
            }
            int a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
            for (int i = 0; i < 64; i++) {
                int s1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
                int ch = (e & f) ^ (~e & g);
                int t1 = hh + s1 + ch + K[i] + w[i];
                int s0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
                int maj = (a & b) ^ (a & c) ^ (b & c);
                int t2 = s0 + maj;
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
        }
    }
}
