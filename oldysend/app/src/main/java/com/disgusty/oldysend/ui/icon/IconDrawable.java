package com.disgusty.oldysend.ui.icon;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Draws a 24x24 SVG path (see {@link IconData}) scaled into the drawable bounds.
 * Uses only Android 1.0 (API 1) APIs; works in software and hardware rendering.
 */
public class IconDrawable extends Drawable {
    private static final float VIEWPORT = 24f;

    private final Path srcPath;
    private final Path drawPath = new Path();
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int sizePx;
    private int color;
    private Shader shader;
    private int alpha = 255;
    private float alphaMultiplier = 1f;
    private float paddingFraction;

    private boolean shadowEnabled;
    private float shadowRadius;
    private float shadowDx;
    private float shadowDy;
    private int shadowColor;
    private boolean innerShadow;

    private int builtW = -1;
    private int builtH = -1;

    public IconDrawable(String pathData, int sizePx, int color) {
        this.srcPath = SvgPath.parse(pathData);
        this.sizePx = sizePx;
        this.color = color;
        paint.setStyle(Paint.Style.FILL);
        shadowPaint.setStyle(Paint.Style.FILL);
        drawPath.setFillType(Path.FillType.WINDING);
    }

    public void setColor(int color) {
        if (this.color != color) {
            this.color = color;
            invalidateSelf();
        }
    }

    public int getColor() {
        return color;
    }

    /**
     * When non-null the shader is used instead of the color. Shader coordinates are local to
     * the bounds: (0,0) is the top-left corner of {@link #getBounds()}.
     */
    public void setShader(Shader shader) {
        this.shader = shader;
        invalidateSelf();
    }

    /** Multiplies the overall alpha (0..1), on top of {@link #setAlpha(int)} and the color alpha. */
    public void setAlphaMultiplier(float multiplier) {
        if (multiplier < 0f) {
            multiplier = 0f;
        } else if (multiplier > 1f) {
            multiplier = 1f;
        }
        alphaMultiplier = multiplier;
        invalidateSelf();
    }

    /** Insets the glyph by {@code fraction} of the smaller bounds dimension on every side (0..0.5). */
    public void setPaddingFraction(float fraction) {
        if (fraction < 0f) {
            fraction = 0f;
        } else if (fraction > 0.49f) {
            fraction = 0.49f;
        }
        paddingFraction = fraction;
        builtW = -1;
        invalidateSelf();
    }

    /**
     * Draws a solid copy of the glyph offset by (dx, dy) px in {@code color} underneath it,
     * imitating Android 2.x icon shadows. If radius > 0 the shadow is softened by stacking a few
     * faint copies within that radius (no BlurMaskFilter). Pass color 0 to disable.
     */
    public void setShadow(float radius, float dx, float dy, int color) {
        shadowEnabled = Color.alpha(color) != 0;
        shadowRadius = radius < 0 ? 0 : radius;
        shadowDx = dx;
        shadowDy = dy;
        shadowColor = color;
        invalidateSelf();
    }

    /** Reserved; inner shadows are not rendered (no API-1-safe antialiased clip available). */
    public void setInnerShadow(boolean enabled) {
        innerShadow = enabled;
    }

    @Override
    public int getIntrinsicWidth() {
        return sizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return sizePx;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        builtW = -1;
    }

    private void buildPath(int w, int h) {
        if (w == builtW && h == builtH) {
            return;
        }
        float min = w < h ? w : h;
        float pad = min * paddingFraction;
        float size = min - 2 * pad;
        float scale = size / VIEWPORT;
        matrix.setScale(scale, scale);
        matrix.postTranslate((w - size) / 2f, (h - size) / 2f);
        srcPath.transform(matrix, drawPath);
        builtW = w;
        builtH = h;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        int w = b.right - b.left;
        int h = b.bottom - b.top;
        if (w <= 0 || h <= 0) {
            return;
        }
        float globalAlpha = (alpha / 255f) * alphaMultiplier;
        if (globalAlpha <= 0f) {
            return;
        }
        buildPath(w, h);

        int save = canvas.save();
        canvas.translate(b.left, b.top);

        if (shadowEnabled) {
            int sa = Math.round(Color.alpha(shadowColor) * globalAlpha);
            if (sa > 0) {
                int rgb = shadowColor & 0x00FFFFFF;
                if (shadowRadius > 0f) {
                    // Soft shadow: centre copy at ~45 % + 8 surrounding copies.
                    float r = shadowRadius * 0.5f;
                    int ring = Math.max(1, sa / 8);
                    shadowPaint.setColor(rgb | (ring << 24));
                    for (int k = 0; k < 8; k++) {
                        double ang = k * Math.PI / 4;
                        drawShifted(canvas, shadowDx + (float) Math.cos(ang) * r,
                                shadowDy + (float) Math.sin(ang) * r, shadowPaint);
                    }
                    shadowPaint.setColor(rgb | ((sa * 45 / 100) << 24));
                } else {
                    shadowPaint.setColor(rgb | (sa << 24));
                }
                drawShifted(canvas, shadowDx, shadowDy, shadowPaint);
            }
        }

        if (shader != null) {
            paint.setShader(shader);
            paint.setColor(0xFF000000);
            paint.setAlpha(Math.round(255 * globalAlpha));
        } else {
            paint.setShader(null);
            paint.setColor(color);
            paint.setAlpha(Math.round(Color.alpha(color) * globalAlpha));
        }
        canvas.drawPath(drawPath, paint);
        canvas.restoreToCount(save);
    }

    private void drawShifted(Canvas canvas, float dx, float dy, Paint p) {
        if (dx == 0f && dy == 0f) {
            canvas.drawPath(drawPath, p);
            return;
        }
        canvas.translate(dx, dy);
        canvas.drawPath(drawPath, p);
        canvas.translate(-dx, -dy);
    }

    @Override
    public void setAlpha(int alpha) {
        if (alpha < 0) {
            alpha = 0;
        } else if (alpha > 255) {
            alpha = 255;
        }
        if (this.alpha != alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
