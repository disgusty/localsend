package com.disgusty.oldysend.ui.icon;

import android.graphics.Path;

import java.util.HashMap;

/**
 * Minimal SVG path-data parser that builds an {@link Path} using only Android 1.0 (API 1) APIs.
 *
 * <p>Supports M L H V C S Q T A Z in absolute and relative form, implicit repeated parameters,
 * compact number syntax ("1.5.5", "-1-2", exponents) and compact arc flags ("a1 1 0 011 1").
 * Arcs are converted to cubic Beziers (segments of at most 90 degrees).
 *
 * <p>Parsed paths are cached; the returned instance is shared and MUST NOT be modified
 * (use {@code path.transform(matrix, dst)} or {@code new Path(path)} to derive a copy).
 */
public final class SvgPath {
    private static final HashMap<String, Path> CACHE = new HashMap<String, Path>();

    private SvgPath() {
    }

    /** Returns the (cached, shared, read-only) path for {@code d}. Never returns null. */
    public static Path parse(String d) {
        if (d == null) {
            d = "";
        }
        synchronized (CACHE) {
            Path p = CACHE.get(d);
            if (p == null) {
                p = new Path();
                p.setFillType(Path.FillType.WINDING);
                try {
                    new Parser(d, p).run();
                } catch (RuntimeException e) {
                    // Malformed data: keep whatever was built so far.
                }
                CACHE.put(d, p);
            }
            return p;
        }
    }

    /** Parses into a fresh, uncached path (caller owns it). */
    public static Path parseUncached(String d) {
        Path p = new Path();
        p.setFillType(Path.FillType.WINDING);
        if (d != null) {
            try {
                new Parser(d, p).run();
            } catch (RuntimeException e) {
                // ignore
            }
        }
        return p;
    }

    /** Drops all cached paths. */
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static final class Parser {
        private final String s;
        private final int n;
        private final Path path;
        private int i;

        // current point, subpath start, last control point (for S/T reflection)
        private float cx, cy, sx, sy, lcx, lcy;
        private char lastCmd;

        Parser(String s, Path path) {
            this.s = s;
            this.n = s.length();
            this.path = path;
        }

        void run() {
            char cmd = 0;
            while (true) {
                skipSeparators();
                if (i >= n) {
                    return;
                }
                char c = s.charAt(i);
                if (isCommand(c)) {
                    cmd = c;
                    i++;
                } else if (cmd == 0 || cmd == 'Z' || cmd == 'z' || !isNumberStart(c)) {
                    throw new IllegalArgumentException("bad path data at " + i);
                }
                // otherwise: implicit repetition of cmd
                cmd = segment(cmd);
            }
        }

        /** Executes one segment; returns the command to use for implicit repeats. */
        private char segment(char cmd) {
            boolean rel = cmd >= 'a' && cmd <= 'z';
            float ox = rel ? cx : 0f;
            float oy = rel ? cy : 0f;
            char up = rel ? (char) (cmd - 32) : cmd;
            char next = cmd;
            switch (up) {
                case 'M': {
                    float x = num() + ox;
                    float y = num() + oy;
                    path.moveTo(x, y);
                    cx = sx = x;
                    cy = sy = y;
                    next = rel ? 'l' : 'L';
                    break;
                }
                case 'L': {
                    float x = num() + ox;
                    float y = num() + oy;
                    path.lineTo(x, y);
                    cx = x;
                    cy = y;
                    break;
                }
                case 'H': {
                    cx = num() + ox;
                    path.lineTo(cx, cy);
                    break;
                }
                case 'V': {
                    cy = num() + oy;
                    path.lineTo(cx, cy);
                    break;
                }
                case 'C': {
                    float x1 = num() + ox, y1 = num() + oy;
                    float x2 = num() + ox, y2 = num() + oy;
                    float x = num() + ox, y = num() + oy;
                    path.cubicTo(x1, y1, x2, y2, x, y);
                    lcx = x2;
                    lcy = y2;
                    cx = x;
                    cy = y;
                    break;
                }
                case 'S': {
                    float x1 = cx, y1 = cy;
                    if (lastCmd == 'C' || lastCmd == 'S') {
                        x1 = 2 * cx - lcx;
                        y1 = 2 * cy - lcy;
                    }
                    float x2 = num() + ox, y2 = num() + oy;
                    float x = num() + ox, y = num() + oy;
                    path.cubicTo(x1, y1, x2, y2, x, y);
                    lcx = x2;
                    lcy = y2;
                    cx = x;
                    cy = y;
                    break;
                }
                case 'Q': {
                    float x1 = num() + ox, y1 = num() + oy;
                    float x = num() + ox, y = num() + oy;
                    path.quadTo(x1, y1, x, y);
                    lcx = x1;
                    lcy = y1;
                    cx = x;
                    cy = y;
                    break;
                }
                case 'T': {
                    float x1 = cx, y1 = cy;
                    if (lastCmd == 'Q' || lastCmd == 'T') {
                        x1 = 2 * cx - lcx;
                        y1 = 2 * cy - lcy;
                    }
                    float x = num() + ox, y = num() + oy;
                    path.quadTo(x1, y1, x, y);
                    lcx = x1;
                    lcy = y1;
                    cx = x;
                    cy = y;
                    break;
                }
                case 'A': {
                    float rx = num(), ry = num(), rot = num();
                    boolean large = flag(), sweep = flag();
                    float x = num() + ox, y = num() + oy;
                    arcTo(path, cx, cy, x, y, rx, ry, rot, large, sweep);
                    cx = x;
                    cy = y;
                    break;
                }
                case 'Z': {
                    path.close();
                    cx = sx;
                    cy = sy;
                    // Android's Path starts the next contour at the last moveTo point.
                    break;
                }
                default:
                    throw new IllegalArgumentException("unknown command " + cmd);
            }
            lastCmd = up;
            return next;
        }

        private static boolean isCommand(char c) {
            switch (c) {
                case 'M': case 'm': case 'L': case 'l': case 'H': case 'h': case 'V': case 'v':
                case 'C': case 'c': case 'S': case 's': case 'Q': case 'q': case 'T': case 't':
                case 'A': case 'a': case 'Z': case 'z':
                    return true;
                default:
                    return false;
            }
        }

        private static boolean isNumberStart(char c) {
            return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.';
        }

        private void skipSeparators() {
            while (i < n) {
                char c = s.charAt(i);
                if (c == ' ' || c == ',' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
                    i++;
                } else {
                    break;
                }
            }
        }

        private boolean flag() {
            skipSeparators();
            if (i >= n) {
                throw new IllegalArgumentException("flag expected");
            }
            char c = s.charAt(i++);
            if (c == '0') {
                return false;
            }
            if (c == '1') {
                return true;
            }
            throw new IllegalArgumentException("bad flag at " + (i - 1));
        }

        private float num() {
            skipSeparators();
            int start = i;
            if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                i++;
            }
            boolean digits = false;
            while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                i++;
                digits = true;
            }
            if (i < n && s.charAt(i) == '.') {
                i++;
                while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                    i++;
                    digits = true;
                }
            }
            if (!digits) {
                throw new IllegalArgumentException("number expected at " + start);
            }
            if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                int save = i;
                i++;
                if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                    i++;
                }
                int expStart = i;
                while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                    i++;
                }
                if (i == expStart) {
                    i = save; // not an exponent
                }
            }
            return Float.parseFloat(s.substring(start, i));
        }
    }

    /**
     * Appends an SVG elliptical arc from (x0,y0) to (x,y) as cubic Beziers
     * (SVG 1.1 implementation notes F.6.5 / F.6.6).
     */
    static void arcTo(Path p, float x0, float y0, float x, float y, float rxf, float ryf,
                      float angleDeg, boolean largeArc, boolean sweep) {
        if (x0 == x && y0 == y) {
            return;
        }
        double rx = Math.abs(rxf);
        double ry = Math.abs(ryf);
        if (rx == 0 || ry == 0) {
            p.lineTo(x, y);
            return;
        }
        double phi = Math.toRadians(angleDeg % 360.0);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);

        double dx2 = (x0 - x) / 2.0;
        double dy2 = (y0 - y) / 2.0;
        double x1p = cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;

        double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
        if (lambda > 1) {
            double sq = Math.sqrt(lambda);
            rx *= sq;
            ry *= sq;
        }
        double rx2 = rx * rx;
        double ry2 = ry * ry;
        double num = rx2 * ry2 - rx2 * y1p * y1p - ry2 * x1p * x1p;
        double den = rx2 * y1p * y1p + ry2 * x1p * x1p;
        double coef = (den == 0 || num <= 0) ? 0 : Math.sqrt(num / den);
        if (largeArc == sweep) {
            coef = -coef;
        }
        double cxp = coef * (rx * y1p / ry);
        double cyp = coef * -(ry * x1p / rx);
        double cxc = cosPhi * cxp - sinPhi * cyp + (x0 + x) / 2.0;
        double cyc = sinPhi * cxp + cosPhi * cyp + (y0 + y) / 2.0;

        double theta1 = Math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx);
        double theta2 = Math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx);
        double dTheta = theta2 - theta1;
        if (sweep && dTheta < 0) {
            dTheta += 2 * Math.PI;
        } else if (!sweep && dTheta > 0) {
            dTheta -= 2 * Math.PI;
        }

        int segs = (int) Math.ceil(Math.abs(dTheta) / (Math.PI / 2) - 1e-7);
        if (segs < 1) {
            segs = 1;
        }
        double delta = dTheta / segs;
        double t = 4.0 / 3.0 * Math.tan(delta / 4);

        double theta = theta1;
        double cosT = Math.cos(theta);
        double sinT = Math.sin(theta);
        // start point and derivative of the ellipse at theta
        double ex = x0;
        double ey = y0;
        double dex = -rx * cosPhi * sinT - ry * sinPhi * cosT;
        double dey = -rx * sinPhi * sinT + ry * cosPhi * cosT;
        for (int k = 0; k < segs; k++) {
            double theta2k = theta + delta;
            double cos2 = Math.cos(theta2k);
            double sin2 = Math.sin(theta2k);
            double ex2 = cxc + rx * cosPhi * cos2 - ry * sinPhi * sin2;
            double ey2 = cyc + rx * sinPhi * cos2 + ry * cosPhi * sin2;
            double dex2 = -rx * cosPhi * sin2 - ry * sinPhi * cos2;
            double dey2 = -rx * sinPhi * sin2 + ry * cosPhi * cos2;
            if (k == segs - 1) {
                ex2 = x;
                ey2 = y;
            }
            p.cubicTo((float) (ex + t * dex), (float) (ey + t * dey),
                    (float) (ex2 - t * dex2), (float) (ey2 - t * dey2),
                    (float) ex2, (float) ey2);
            theta = theta2k;
            ex = ex2;
            ey = ey2;
            dex = dex2;
            dey = dey2;
        }
    }
}
