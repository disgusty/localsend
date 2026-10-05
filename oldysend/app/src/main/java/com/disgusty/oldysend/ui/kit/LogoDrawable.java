package com.disgusty.oldysend.ui.kit;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import com.disgusty.oldysend.ui.icon.IconData;
import com.disgusty.oldysend.ui.icon.SvgPath;

/**
 * OldySend logo: a disc with a ring and a paper plane, drawn in the style's colors.
 * Classic passes a gradient shader to get the glossy Android 2.x icon look.
 */
public class LogoDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private final int ring;
    private final int fill;
    private final int glyph;
    private Shader fillShader;
    private boolean gloss;
    private final Path plane = new Path();
    private float angle;

    /** Rotates only the paper plane, so ring, gradient and gloss keep their lighting. */
    public void setAngle(float degrees) {
        angle = degrees;
        invalidateSelf();
    }

    public LogoDrawable(int size, int ring, int fill, int glyph) {
        this.size = size;
        this.ring = ring;
        this.fill = fill;
        this.glyph = glyph;
    }

    public LogoDrawable shader(Shader s) {
        fillShader = s;
        return this;
    }

    public LogoDrawable gloss() {
        gloss = true;
        return this;
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
        float s = Math.min(b.width(), b.height());
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        float r = s / 2f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ring);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setColor(fill);
        if (fillShader != null) {
            canvas.save();
            canvas.translate(cx - r, cy - r);
            paint.setShader(fillShader);
            canvas.drawCircle(r, r, r * 0.86f, paint);
            paint.setShader(null);
            canvas.restore();
        } else {
            canvas.drawCircle(cx, cy, r * 0.86f, paint);
        }
        // Paper plane, rotated like a launch.
        Path src = SvgPath.parse(IconData.M1_SEND);
        Matrix m = new Matrix();
        float g = s * 0.5f / 24f;
        m.postTranslate(-12.5f, -12f);
        m.postRotate(-25 + angle);
        m.postScale(g, g);
        m.postTranslate(cx + s * 0.02f, cy);
        src.transform(m, plane);
        paint.setColor(glyph);
        canvas.drawPath(plane, paint);
        if (gloss) {
            // Upper highlight kept inside the disc (no clipPath: unsupported with hardware acceleration before 4.3).
            paint.setColor(0x40FFFFFF);
            canvas.drawOval(new android.graphics.RectF(cx - r * 0.66f, cy - r * 0.82f, cx + r * 0.66f, cy + r * 0.02f), paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
