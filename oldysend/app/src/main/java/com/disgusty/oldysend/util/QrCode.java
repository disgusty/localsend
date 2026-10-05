package com.disgusty.oldysend.util;

import java.io.UnsupportedEncodingException;

/**
 * Minimal QR Code Model 2 encoder (ISO/IEC 18004). Structure follows the algorithm of
 * Project Nayuki's "QR Code generator library" (MIT License), reimplemented for Java 5.
 */
public final class QrCode {
    /** Error correction level L (~7% recovery). */
    public static final int ECL_L = 0;
    /** Error correction level M (~15% recovery). */
    public static final int ECL_M = 1;
    /** Error correction level Q (~25% recovery). */
    public static final int ECL_Q = 2;
    /** Error correction level H (~30% recovery). */
    public static final int ECL_H = 3;

    private static final int[] FORMAT_ECL_BITS = {1, 0, 3, 2};
    private static final String ALNUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

    private static final byte[][] ECC_PER_BLOCK = {
        {-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
        {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},
        {-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
        {-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
    };

    private static final byte[][] NUM_BLOCKS = {
        {-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},
        {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},
        {-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},
        {-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},
    };

    /** Side length in modules (21..177). */
    public final int size;
    /** Version number 1..40. */
    public final int version;
    /** Mask pattern 0..7 that was applied. */
    public final int mask;

    private final boolean[][] modules;
    private final boolean[][] isFunction;

    /** Encodes text at the given ECL, choosing the smallest version and the best mask. */
    public static QrCode encode(String text, int ecl) {
        return encode(text, ecl, -1);
    }

    /** Like {@link #encode(String, int)} but with a fixed mask 0..7, or -1 for automatic. */
    public static QrCode encode(String text, int ecl, int mask) {
        if (ecl < ECL_L || ecl > ECL_H || mask < -1 || mask > 7) {
            throw new IllegalArgumentException();
        }
        int mode = 1; // 1 numeric, 2 alphanumeric, 4 byte
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                mode = ALNUM.indexOf(c) >= 0 ? 2 : 4;
                if (mode == 4) {
                    break;
                }
            }
        }
        if (mode != 4) {
            for (int i = 0; i < text.length(); i++) {
                if (ALNUM.indexOf(text.charAt(i)) < 0) {
                    mode = 4;
                    break;
                }
            }
        }
        byte[] bytes = null;
        int count = text.length();
        int payloadBits;
        if (mode == 4) {
            try {
                bytes = text.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            count = bytes.length;
            payloadBits = count * 8;
        } else if (mode == 2) {
            payloadBits = count / 2 * 11 + (count % 2) * 6;
        } else {
            payloadBits = count / 3 * 10 + (count % 3 == 0 ? 0 : count % 3 == 1 ? 4 : 7);
        }

        int ver;
        int capacityBits = 0;
        int usedBits = 0;
        for (ver = 1; ; ver++) {
            if (ver > 40) {
                throw new IllegalArgumentException("Data too long for a QR code");
            }
            int ccBits = countBits(mode, ver);
            capacityBits = dataCodewords(ver, ecl) * 8;
            usedBits = 4 + ccBits + payloadBits;
            if (count < (1 << ccBits) && usedBits <= capacityBits) {
                break;
            }
        }

        BitWriter bw = new BitWriter(capacityBits / 8);
        bw.put(mode, 4);
        bw.put(count, countBits(mode, ver));
        if (mode == 4) {
            for (int i = 0; i < count; i++) {
                bw.put(bytes[i] & 0xFF, 8);
            }
        } else if (mode == 2) {
            int i = 0;
            for (; i + 1 < count; i += 2) {
                bw.put(ALNUM.indexOf(text.charAt(i)) * 45 + ALNUM.indexOf(text.charAt(i + 1)), 11);
            }
            if (i < count) {
                bw.put(ALNUM.indexOf(text.charAt(i)), 6);
            }
        } else {
            for (int i = 0; i < count; i += 3) {
                int n = Math.min(3, count - i);
                bw.put(Integer.parseInt(text.substring(i, i + n)), n * 3 + 1);
            }
        }
        bw.put(0, Math.min(4, capacityBits - bw.length));
        bw.put(0, (8 - bw.length % 8) % 8);
        for (int pad = 0xEC; bw.length < capacityBits; pad ^= 0xEC ^ 0x11) {
            bw.put(pad, 8);
        }
        return new QrCode(ver, ecl, bw.data, mask);
    }

    private QrCode(int ver, int ecl, byte[] data, int msk) {
        version = ver;
        size = ver * 4 + 17;
        modules = new boolean[size][size];
        isFunction = new boolean[size][size];
        drawFunctionPatterns(ecl);
        drawCodewords(addEccAndInterleave(data, ver, ecl));
        if (msk == -1) {
            int best = Integer.MAX_VALUE;
            for (int m = 0; m < 8; m++) {
                applyMask(m);
                drawFormatBits(ecl, m);
                int p = penalty();
                if (p < best) {
                    best = p;
                    msk = m;
                }
                applyMask(m);
            }
        }
        mask = msk;
        applyMask(msk);
        drawFormatBits(ecl, msk);
    }

    /** Returns true for a dark module; coordinates outside the symbol are light. */
    public boolean get(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size && modules[y][x];
    }

    // ---- function patterns ----

    private void drawFunctionPatterns(int ecl) {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        int[] pos = alignmentPositions();
        int n = pos.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                boolean corner = (i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0);
                if (!corner) {
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            setFunction(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                        }
                    }
                }
            }
        }
        drawFormatBits(ecl, 0); // reserve the area; real bits drawn after masking
        if (version >= 7) {
            int rem = version;
            for (int i = 0; i < 12; i++) {
                rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            }
            int bits = version << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean bit = ((bits >>> i) & 1) != 0;
                int a = size - 11 + i % 3;
                int b = i / 3;
                setFunction(a, b, bit);
                setFunction(b, a, bit);
            }
        }
    }

    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) {
                    int d = Math.max(Math.abs(dx), Math.abs(dy));
                    setFunction(x, y, d != 2 && d != 4);
                }
            }
        }
    }

    private void drawFormatBits(int ecl, int msk) {
        int data = FORMAT_ECL_BITS[ecl] << 3 | msk;
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) {
            setFunction(8, i, bit(bits, i));
        }
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) {
            setFunction(14 - i, 8, bit(bits, i));
        }
        for (int i = 0; i < 8; i++) {
            setFunction(size - 1 - i, 8, bit(bits, i));
        }
        for (int i = 8; i < 15; i++) {
            setFunction(8, size - 15 + i, bit(bits, i));
        }
        setFunction(8, size - 8, true); // dark module
    }

    private static boolean bit(int v, int i) {
        return ((v >>> i) & 1) != 0;
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private int[] alignmentPositions() {
        if (version == 1) {
            return new int[0];
        }
        int n = version / 7 + 2;
        int step = version == 32 ? 26 : (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] result = new int[n];
        result[0] = 6;
        for (int i = n - 1, p = size - 7; i >= 1; i--, p -= step) {
            result[i] = p;
        }
        return result;
    }

    // ---- data and error correction ----

    private static int countBits(int mode, int ver) {
        int g = ver <= 9 ? 0 : ver <= 26 ? 1 : 2;
        if (mode == 1) {
            return 10 + g * 2;
        }
        if (mode == 2) {
            return 9 + g * 2;
        }
        return g == 0 ? 8 : 16;
    }

    private static int rawDataModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int n = ver / 7 + 2;
            result -= (25 * n - 10) * n - 55;
            if (ver >= 7) {
                result -= 36;
            }
        }
        return result;
    }

    private static int dataCodewords(int ver, int ecl) {
        return rawDataModules(ver) / 8 - ECC_PER_BLOCK[ecl][ver] * NUM_BLOCKS[ecl][ver];
    }

    private static byte[] addEccAndInterleave(byte[] data, int ver, int ecl) {
        int numBlocks = NUM_BLOCKS[ecl][ver];
        int eccLen = ECC_PER_BLOCK[ecl][ver];
        int raw = rawDataModules(ver) / 8;
        int numShort = numBlocks - raw % numBlocks;
        int shortDataLen = raw / numBlocks - eccLen;
        byte[] gen = rsGenerator(eccLen);
        byte[][] blocks = new byte[numBlocks][];
        byte[][] eccs = new byte[numBlocks][];
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int len = shortDataLen + (i < numShort ? 0 : 1);
            blocks[i] = new byte[len];
            System.arraycopy(data, k, blocks[i], 0, len);
            k += len;
            eccs[i] = rsRemainder(blocks[i], gen);
        }
        byte[] out = new byte[raw];
        int o = 0;
        for (int i = 0; i <= shortDataLen; i++) {
            for (int j = 0; j < numBlocks; j++) {
                if (i < blocks[j].length) {
                    out[o++] = blocks[j][i];
                }
            }
        }
        for (int i = 0; i < eccLen; i++) {
            for (int j = 0; j < numBlocks; j++) {
                out[o++] = eccs[j][i];
            }
        }
        return out;
    }

    /** Generator polynomial coefficients (highest-degree term implicit), roots 2^0..2^(degree-1). */
    private static byte[] rsGenerator(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) gfMul(result[j] & 0xFF, root);
                if (j + 1 < degree) {
                    result[j] ^= result[j + 1];
                }
            }
            root = gfMul(root, 2);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, byte[] gen) {
        byte[] result = new byte[gen.length];
        for (int i = 0; i < data.length; i++) {
            int factor = (data[i] ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int j = 0; j < result.length; j++) {
                result[j] ^= (byte) gfMul(gen[j] & 0xFF, factor);
            }
        }
        return result;
    }

    /** Multiplication in GF(2^8) modulo x^8 + x^4 + x^3 + x^2 + 1. */
    private static int gfMul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        int total = data.length * 8;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            boolean upward = ((right + 1) & 2) == 0;
            for (int vert = 0; vert < size; vert++) {
                int y = upward ? size - 1 - vert : vert;
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    if (!isFunction[y][x] && i < total) {
                        modules[y][x] = ((data[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
                        i++;
                    }
                }
            }
        }
    }

    // ---- masking ----

    private void applyMask(int m) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (m) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (invert && !isFunction[y][x]) {
                    modules[y][x] = !modules[y][x];
                }
            }
        }
    }

    /** Standard penalty score (rules N1..N4) of the current matrix. */
    private int penalty() {
        int score = 0;
        int dark = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                int run = 0;
                boolean last = false;
                for (int b = 0; b < size; b++) {
                    boolean c = pass == 0 ? modules[a][b] : modules[b][a];
                    if (b > 0 && c == last) {
                        run++;
                        if (run == 5) {
                            score += 3;
                        } else if (run > 5) {
                            score++;
                        }
                    } else {
                        run = 1;
                        last = c;
                    }
                    if (b + 10 < size && finderLike(pass, a, b)) {
                        score += 40;
                    }
                }
            }
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean c = modules[y][x];
                if (c) {
                    dark++;
                }
                if (x + 1 < size && y + 1 < size && c == modules[y][x + 1]
                        && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    score += 3;
                }
            }
        }
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        return score + k * 10;
    }

    private static final int PAT_A = 0x5D0; // 1011101 0000
    private static final int PAT_B = 0x05D; // 0000 1011101

    private boolean finderLike(int pass, int a, int b) {
        int v = 0;
        for (int i = 0; i < 11; i++) {
            boolean c = pass == 0 ? modules[a][b + i] : modules[b + i][a];
            v = v << 1 | (c ? 1 : 0);
        }
        return v == PAT_A || v == PAT_B;
    }

    private static final class BitWriter {
        final byte[] data;
        int length;

        BitWriter(int bytes) {
            data = new byte[bytes];
        }

        void put(int value, int bits) {
            for (int i = bits - 1; i >= 0; i--, length++) {
                if (((value >>> i) & 1) != 0) {
                    data[length >>> 3] |= (byte) (0x80 >>> (length & 7));
                }
            }
        }
    }
}
