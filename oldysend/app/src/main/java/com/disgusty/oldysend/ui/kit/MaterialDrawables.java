package com.disgusty.oldysend.ui.kit;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** Animated Material controls (switch, checkbox, radio, progress, spinner) for Material 1 and Material 3. */
final class MaterialDrawables {
    private MaterialDrawables() {
    }

    static boolean has(int[] state, int attr) {
        for (int s : state) if (s == attr) return true;
        return false;
    }

    /** Base: tracks checked/pressed/enabled state and animates a 0..1 "checked" fraction. */
    abstract static class Animated extends Drawable {
        protected final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        protected boolean checked;
        protected boolean pressed;
        protected boolean enabled = true;
        protected float fraction;
        private float from;
        private long start;
        private final long duration;
        private boolean first = true;
        protected final boolean animate;

        Animated(long duration, boolean animate) {
            this.duration = duration;
            this.animate = animate;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean c = has(state, android.R.attr.state_checked);
            boolean p = has(state, android.R.attr.state_pressed);
            boolean e = !has(state, -android.R.attr.state_enabled) && enabledFrom(state);
            boolean changed = c != checked || p != pressed || e != enabled;
            if (c != checked) {
                if (first || !animate) {
                    fraction = c ? 1 : 0;
                    start = 0;
                } else {
                    from = fraction;
                    start = SystemClock.uptimeMillis();
                }
            }
            first = false;
            checked = c;
            pressed = p;
            enabled = e;
            if (changed) invalidateSelf();
            return changed;
        }

        private static boolean enabledFrom(int[] state) {
            // A view state set always contains state_enabled when enabled.
            return has(state, android.R.attr.state_enabled) || state.length == 0;
        }

        /** Advances the animation; returns true while running. */
        protected boolean step() {
            if (start == 0) {
                fraction = checked ? 1 : 0;
                return false;
            }
            float t = Math.min(1f, (SystemClock.uptimeMillis() - start) / (float) duration);
            float eased = 1 - (1 - t) * (1 - t) * (1 - t);
            float target = checked ? 1 : 0;
            fraction = from + (target - from) * eased;
            if (t >= 1f) {
                start = 0;
                return false;
            }
            invalidateSelf();
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        static int lerp(int a, int b, float f) {
            int aa = (a >>> 24), ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
            int ba = (b >>> 24), br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
            return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                    | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
        }
    }

    /** Material 3 switch: 52×32 track, 16→24 dp thumb (28 pressed), outline when off. */
    static final class M3Switch extends Animated {
        private final float d;
        private final Scheme c;
        private final RectF r = new RectF();

        M3Switch(Scheme c, float density, boolean animate) {
            super(250, animate);
            this.c = c;
            this.d = density;
        }

        @Override
        public int getIntrinsicWidth() {
            return Math.round(52 * d);
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.round(32 * d);
        }

        @Override
        public void draw(Canvas canvas) {
            step();
            Rect b = getBounds();
            float f = fraction;
            r.set(b.left, b.top, b.right, b.bottom);
            int trackOff = c.surfaceContainerHighest;
            int trackOn = c.primary;
            int track = lerp(trackOff, trackOn, f);
            if (!enabled) track = Scheme.withAlpha(checked ? c.onSurface : c.surfaceContainerHighest, checked ? 0.12f : 0.12f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(track);
            canvas.drawRoundRect(r, 16 * d, 16 * d, paint);
            if (f < 1) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * d);
                int outline = enabled ? c.outline : Scheme.withAlpha(c.onSurface, 0.12f);
                paint.setColor(Scheme.withAlpha(outline, 1 - f));
                r.inset(d, d);
                canvas.drawRoundRect(r, 15 * d, 15 * d, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            float size = pressed ? 28 : (16 + 8 * f);
            float cx = b.left + (16 + 20 * f) * d;
            float cy = b.exactCenterY();
            int thumb = lerp(c.outline, c.onPrimary, f);
            if (!enabled) thumb = checked ? c.surface : Scheme.withAlpha(c.onSurface, 0.38f);
            if (pressed && enabled) {
                paint.setColor(Scheme.withAlpha(checked ? c.primary : c.onSurface, 0.12f));
                canvas.drawCircle(cx, cy, 20 * d, paint);
                if (checked) thumb = c.primaryContainer;
                else thumb = c.onSurfaceVariant;
            }
            paint.setColor(thumb);
            canvas.drawCircle(cx, cy, size * d / 2, paint);
        }
    }

    /** Material 1 switch (Android 5.0 SwitchCompat): 34×14 track, 20 dp thumb with shadow. */
    static final class M1Switch extends Animated {
        private final float d;
        private final int accent;
        private final boolean dark;
        private final RectF r = new RectF();

        M1Switch(int accent, boolean dark, float density, boolean animate) {
            super(150, animate);
            this.accent = accent;
            this.dark = dark;
            this.d = density;
        }

        @Override
        public int getIntrinsicWidth() {
            return Math.round(48 * d);
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.round(32 * d);
        }

        @Override
        public void draw(Canvas canvas) {
            step();
            Rect b = getBounds();
            float f = fraction;
            float cy = b.exactCenterY();
            float left = b.left + 7 * d;
            r.set(left, cy - 7 * d, left + 34 * d, cy + 7 * d);
            // switch_track_material: 30% alpha of the thumb color (accent when checked, grey/white otherwise)
            int offTrack = dark ? 0x4DFFFFFF : 0x42000000;
            int onTrack = Scheme.withAlpha(accent, 0.5f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(enabled ? lerp(offTrack, onTrack, f) : (dark ? 0x1AFFFFFF : 0x1F000000));
            canvas.drawRoundRect(r, 7 * d, 7 * d, paint);
            float cx = left + 10 * d + 14 * d * f;
            if (pressed && enabled) {
                paint.setColor(Scheme.withAlpha(checked ? accent : (dark ? 0xFFFFFFFF : 0xFF000000), 0.12f));
                canvas.drawCircle(cx, cy, 20 * d, paint);
            }
            // thumb shadow (elevation 1dp approximated)
            paint.setColor(0x33000000);
            canvas.drawCircle(cx, cy + d, 10 * d, paint);
            int offThumb = dark ? 0xFFBDBDBD : 0xFFF1F1F1; // switch_thumb_normal_material_dark / light
            int thumb = enabled ? lerp(offThumb, accent, f) : (dark ? 0xFF616161 : 0xFFBDBDBD);
            paint.setColor(thumb);
            canvas.drawCircle(cx, cy, 10 * d, paint);
        }
    }

    /** Material checkbox: M1 18 dp box with 2 dp border, filled with accent + check; M3 identical geometry. */
    static final class Check extends Animated {
        private final float d;
        private final int on;
        private final int off;
        private final int checkColor;
        private final float radius;
        private final Path path = new Path();
        private final RectF r = new RectF();
        private final int pressedHalo;

        Check(int on, int off, int checkColor, float radiusDp, float density, boolean animate, int pressedHalo) {
            super(150, animate);
            this.on = on;
            this.off = off;
            this.checkColor = checkColor;
            this.d = density;
            this.radius = radiusDp;
            this.pressedHalo = pressedHalo;
        }

        @Override
        public int getIntrinsicWidth() {
            return Math.round(40 * d);
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.round(40 * d);
        }

        @Override
        public void draw(Canvas canvas) {
            step();
            Rect b = getBounds();
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            if (pressed && enabled) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Scheme.withAlpha(checked ? on : pressedHalo, 0.12f));
                canvas.drawCircle(cx, cy, 20 * d, paint);
            }
            float h = 9 * d;
            r.set(cx - h + d, cy - h + d, cx + h - d, cy + h - d);
            int color = enabled ? (fraction > 0.5f ? on : off) : Scheme.withAlpha(off, 0.38f);
            if (fraction > 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(enabled ? on : Scheme.withAlpha(off, 0.38f));
                paint.setAlpha(Math.round(255 * Math.min(1, fraction * 2) * (enabled ? 1 : 0.38f)));
                canvas.drawRoundRect(new RectF(cx - h, cy - h, cx + h, cy + h), radius * d, radius * d, paint);
                paint.setAlpha(255);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * d);
                paint.setStrokeCap(Paint.Cap.SQUARE);
                paint.setColor(checkColor);
                path.reset();
                float p = Math.max(0, (fraction - 0.3f) / 0.7f);
                path.moveTo(cx - 4.5f * d, cy + 0.3f * d);
                float mx = cx - 1.5f * d, my = cy + 3.3f * d;
                if (p < 0.4f) {
                    float q = p / 0.4f;
                    path.lineTo(cx - 4.5f * d + (mx - (cx - 4.5f * d)) * q, cy + 0.3f * d + (my - (cy + 0.3f * d)) * q);
                } else {
                    path.lineTo(mx, my);
                    float q = (p - 0.4f) / 0.6f;
                    path.lineTo(mx + (cx + 5 * d - mx) * q, my + (cy - 3.7f * d - my) * q);
                }
                canvas.drawPath(path, paint);
            }
            if (fraction < 1) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * d);
                paint.setColor(Scheme.withAlpha(color, 1 - fraction));
                canvas.drawRoundRect(r, radius * d, radius * d, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** Material radio button: 20 dp ring, 10 dp dot growing in. */
    static final class Radio extends Animated {
        private final float d;
        private final int on;
        private final int off;
        private final int pressedHalo;

        Radio(int on, int off, float density, boolean animate, int pressedHalo) {
            super(150, animate);
            this.on = on;
            this.off = off;
            this.d = density;
            this.pressedHalo = pressedHalo;
        }

        @Override
        public int getIntrinsicWidth() {
            return Math.round(40 * d);
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.round(40 * d);
        }

        @Override
        public void draw(Canvas canvas) {
            step();
            Rect b = getBounds();
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            if (pressed && enabled) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Scheme.withAlpha(checked ? on : pressedHalo, 0.12f));
                canvas.drawCircle(cx, cy, 20 * d, paint);
            }
            int color = lerp(off, on, fraction);
            if (!enabled) color = Scheme.withAlpha(off, 0.38f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * d);
            paint.setColor(color);
            canvas.drawCircle(cx, cy, 9 * d, paint);
            if (fraction > 0) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, 5 * d * fraction, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** Linear progress: determinate bar or the Material indeterminate two-segment animation. */
    static final class Linear extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int track;
        private final int indicator;
        private final float height;
        private final boolean rounded;
        private final float gap;
        float progress;
        boolean indeterminate;
        private final long start = SystemClock.uptimeMillis();

        Linear(int track, int indicator, float height, boolean rounded, float gap) {
            this.track = track;
            this.indicator = indicator;
            this.height = height;
            this.rounded = rounded;
            this.gap = gap;
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.round(height);
        }

        private void bar(Canvas canvas, float l, float r, float top, float bottom, int color) {
            if (r - l <= 0) return;
            paint.setColor(color);
            if (rounded) {
                float rad = (bottom - top) / 2;
                canvas.drawRoundRect(new RectF(l, top, r, bottom), rad, rad, paint);
            } else {
                canvas.drawRect(l, top, r, bottom, paint);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float top = b.exactCenterY() - height / 2, bottom = top + height;
            float w = b.width();
            if (!indeterminate) {
                float x = b.left + w * Math.max(0, Math.min(1, progress));
                bar(canvas, b.left, x, top, bottom, indicator);
                bar(canvas, gap > 0 && progress > 0 ? x + gap : x, b.right, top, bottom, track);
                if (gap > 0) {
                    // Material 3 stop indicator at the end of the track.
                    paint.setColor(indicator);
                    canvas.drawCircle(b.right - height / 2, (top + bottom) / 2, height / 2, paint);
                }
                return;
            }
            bar(canvas, b.left, b.right, top, bottom, track);
            float t = ((SystemClock.uptimeMillis() - start) % 2000) / 2000f;
            // Two segments with the timing of the Material indeterminate animation (approximated).
            float h1 = seg(t, 0f, 0.75f), t1 = seg(t, 0.25f, 1f);
            float h2 = seg(t, 0.55f, 1f), t2 = seg(t, 0.75f, 1.05f);
            bar(canvas, b.left + w * curve(t1) * 1.2f - w * 0.2f, b.left + w * curve(h1) * 1.2f - w * 0.2f, top, bottom, indicator);
            bar(canvas, b.left + w * curve(t2), b.left + w * curve(h2) * 1.1f, top, bottom, indicator);
            invalidateSelf();
        }

        private static float seg(float t, float a, float bnd) {
            return Math.max(0, Math.min(1, (t - a) / (bnd - a)));
        }

        private static float curve(float x) {
            return x < 0.5f ? 2 * x * x : 1 - (float) Math.pow(-2 * x + 2, 2) / 2;
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Circular indeterminate indicator (rotating, growing/shrinking arc). */
    static final class Spinner extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        private final long start = SystemClock.uptimeMillis();
        private final int track;

        Spinner(int color, int track, int sizePx, float stroke, boolean roundCap) {
            this.size = sizePx;
            this.track = track;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(roundCap ? Paint.Cap.ROUND : Paint.Cap.SQUARE);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float s = paint.getStrokeWidth();
            RectF r = new RectF(b.left + s, b.top + s, b.right - s, b.bottom - s);
            long ms = SystemClock.uptimeMillis() - start;
            float cycle = (ms % 1333) / 1333f;
            float rot = (ms % 1568) / 1568f * 360;
            int turn = (int) (ms / 1333) % 4;
            float sweep;
            float head;
            if (cycle < 0.5f) {
                float p = cycle * 2;
                sweep = 10 + 260 * (p < 0.5f ? 2 * p * p : 1 - (float) Math.pow(-2 * p + 2, 2) / 2);
                head = 0;
            } else {
                float p = (cycle - 0.5f) * 2;
                float e = p < 0.5f ? 2 * p * p : 1 - (float) Math.pow(-2 * p + 2, 2) / 2;
                sweep = 270 - 260 * e;
                head = 260 * e;
            }
            if (track != 0) {
                int c = paint.getColor();
                paint.setColor(track);
                canvas.drawArc(r, 0, 360, false, paint);
                paint.setColor(c);
            }
            canvas.drawArc(r, rot + turn * 270 + head - 90, sweep, false, paint);
            invalidateSelf();
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
