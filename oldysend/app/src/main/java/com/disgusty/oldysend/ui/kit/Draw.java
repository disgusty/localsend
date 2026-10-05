package com.disgusty.oldysend.ui.kit;

import android.annotation.TargetApi;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.disgusty.oldysend.util.Sdk;

/** Programmatic drawables usable on every Android version. */
public final class Draw {
    public static final int[] PRESSED = {android.R.attr.state_pressed};
    public static final int[] FOCUSED = {android.R.attr.state_focused};
    public static final int[] SELECTED = {android.R.attr.state_selected};
    public static final int[] CHECKED = {android.R.attr.state_checked};
    public static final int[] DISABLED = {-android.R.attr.state_enabled};
    public static final int[] ANY = {};

    private Draw() {
    }

    public static GradientDrawable rect(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        if (radius > 0) d.setCornerRadius(radius);
        return d;
    }

    public static GradientDrawable stroke(int fill, int strokeColor, int strokeWidth, float radius) {
        GradientDrawable d = rect(fill, radius);
        d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    public static GradientDrawable gradient(int top, int bottom, float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        if (radius > 0) d.setCornerRadius(radius);
        return d;
    }

    /** State list: pressed/focused/selected/checked/disabled/default, nulls skipped. */
    public static StateListDrawable states(Drawable pressed, Drawable focused, Drawable checked, Drawable disabled, Drawable normal) {
        StateListDrawable s = new StateListDrawable();
        if (disabled != null) s.addState(DISABLED, disabled);
        if (pressed != null) s.addState(PRESSED, pressed);
        if (checked != null) s.addState(CHECKED, checked);
        if (checked != null) s.addState(SELECTED, checked);
        if (focused != null) s.addState(FOCUSED, focused);
        s.addState(ANY, normal != null ? normal : new ColorDrawable(0));
        return s;
    }

    /** Material touch feedback: RippleDrawable on 5.0+, an animated circular ink drawable before. */
    public static Drawable ripple(int rippleColor, Drawable content, Drawable mask, boolean unbounded) {
        if (Sdk.atLeast(21)) return Api21.ripple(rippleColor, content, mask);
        InkDrawable ink = new InkDrawable(rippleColor, unbounded, mask instanceof GradientDrawable ? -1 : 0);
        if (content == null) return ink;
        return new LayerDrawable(new Drawable[]{content, ink});
    }

    public static void elevate(View v, float elevationPx, float radius) {
        if (Sdk.atLeast(21)) Api21.elevate(v, elevationPx, radius);
    }

    /**
     * Pre-5.0 replacement for elevation shadows: a soft shadow below a rounded rectangle, drawn with gradients
     * (approximates Material key + ambient shadows; BlurMaskFilter would need software layers).
     */
    public static final class ShadowDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float radius;
        private final float elevation;
        private final RectF rect = new RectF();
        private final Path path = new Path();

        public ShadowDrawable(int color, float radius, float elevation) {
            fill.setColor(color);
            this.radius = radius;
            this.elevation = elevation;
        }

        /** Padding a view needs so its content sits inside the shadow. */
        public int inset() {
            return (int) Math.ceil(elevation * 1.5f);
        }

        @Override
        public boolean getPadding(Rect padding) {
            int i = inset();
            padding.set(i, i / 2, i, i + i / 2);
            return true;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int i = inset();
            rect.set(b.left + i, b.top + i / 2f, b.right - i, b.bottom - i - i / 2f);
            float spread = elevation * 1.2f;
            int layers = Math.max(2, (int) spread);
            for (int l = layers; l >= 1; l--) {
                float f = l / (float) layers;
                int alpha = (int) (28 * (1 - f) * (1 - f) + 2);
                shadow.setColor(alpha << 24);
                float grow = spread * f;
                RectF r = new RectF(rect.left - grow * 0.6f, rect.top - grow * 0.3f + elevation * 0.4f,
                        rect.right + grow * 0.6f, rect.bottom + grow + elevation * 0.4f);
                canvas.drawRoundRect(r, radius + grow, radius + grow, shadow);
            }
            canvas.drawRoundRect(rect, radius, radius, fill);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Circular "ink" reaction expanding from the touch point, for Material styles before Android 5.0. */
    public static final class InkDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final boolean unbounded;
        private final float cornerRadius;
        private float hx = -1;
        private float hy = -1;
        private long pressStart;
        private long releaseStart;
        private boolean pressed;
        private final RectF clip = new RectF();
        private final Path clipPath = new Path();

        InkDrawable(int color, boolean unbounded, float cornerRadius) {
            this.color = color;
            this.unbounded = unbounded;
            this.cornerRadius = cornerRadius;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean p = false;
            for (int s : state) if (s == android.R.attr.state_pressed) p = true;
            if (p == pressed) return false;
            pressed = p;
            long now = SystemClock.uptimeMillis();
            if (p) {
                pressStart = now;
                releaseStart = 0;
            } else {
                releaseStart = now;
            }
            invalidateSelf();
            return true;
        }

        public void setHotspot(float x, float y) {
            hx = x;
            hy = y;
        }

        @Override
        public void draw(Canvas canvas) {
            if (pressStart == 0) return;
            long now = SystemClock.uptimeMillis();
            Rect b = getBounds();
            float maxR = (float) Math.sqrt(b.width() * b.width() + b.height() * b.height()) / (unbounded ? 2.4f : 1.6f);
            float grow = Math.min(1f, (now - pressStart) / 300f);
            float r = maxR * (0.2f + 0.8f * (1 - (1 - grow) * (1 - grow)));
            float alpha = 1f;
            if (releaseStart > 0) {
                float fade = Math.min(1f, (now - releaseStart) / 250f);
                alpha = 1 - fade;
                r = maxR * Math.max(grow, Math.min(1f, (now - pressStart) / 250f));
                if (fade >= 1f) {
                    pressStart = 0;
                    return;
                }
            }
            float cx = unbounded || hx < 0 ? b.exactCenterX() : hx;
            float cy = unbounded || hy < 0 ? b.exactCenterY() : hy;
            paint.setColor(Scheme.withAlpha(color, alpha));
            int save = canvas.save();
            if (!unbounded) {
                clip.set(b);
                if (cornerRadius != 0) {
                    clipPath.reset();
                    clipPath.addRoundRect(clip, Math.max(0, cornerRadius), Math.max(0, cornerRadius), Path.Direction.CW);
                    canvas.clipPath(clipPath);
                } else {
                    canvas.clipRect(b);
                }
                // Background tint while pressed, as in the Material 1 spec.
                canvas.drawColor(Scheme.withAlpha(color, alpha * 0.5f));
            }
            canvas.drawCircle(cx, cy, r, paint);
            canvas.restoreToCount(save);
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

    /** Vertical gradient shader for Android 2.x style icons. */
    public static Shader vertical(float height, int top, int bottom) {
        return new LinearGradient(0, 0, 0, height, top, bottom, Shader.TileMode.CLAMP);
    }

    public static Shader radial(float cx, float cy, float r, int inner, int outer) {
        return new RadialGradient(cx, cy, r, inner, outer, Shader.TileMode.CLAMP);
    }

    @TargetApi(21)
    private static final class Api21 {
        static Drawable ripple(int color, Drawable content, Drawable mask) {
            return new RippleDrawable(ColorStateList.valueOf(color), content, mask);
        }

        static void elevate(View v, float elevation, final float radius) {
            v.setElevation(elevation);
            v.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
        }
    }
}
