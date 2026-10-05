package com.disgusty.oldysend.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.disgusty.oldysend.util.QrCode;

/**
 * A QR code on a white square with the 4-module quiet zone. Always dark on light: many scanners cannot read
 * inverted codes, so it ignores the dark theme.
 */
final class QrDrawable extends Drawable {
    private static final int QUIET = 4;

    private final QrCode qr;
    private final int sizePx;
    private final Paint paint = new Paint();

    QrDrawable(String text, int sizePx) {
        this.qr = QrCode.encode(text, QrCode.ECL_Q);
        this.sizePx = sizePx;
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
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        int side = Math.min(b.width(), b.height());
        int modules = qr.size + 2 * QUIET;
        // Whole pixels per module keep the edges sharp; the rest becomes extra margin.
        int unit = Math.max(1, side / modules);
        int left = b.left + (b.width() - unit * modules) / 2;
        int top = b.top + (b.height() - unit * modules) / 2;
        paint.setColor(0xFFFFFFFF);
        canvas.drawRect(left, top, left + unit * modules, top + unit * modules, paint);
        paint.setColor(0xFF000000);
        for (int y = 0; y < qr.size; y++) {
            int x = 0;
            while (x < qr.size) {
                if (!qr.get(x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < qr.size && qr.get(x, y)) x++;
                canvas.drawRect(left + (QUIET + start) * unit, top + (QUIET + y) * unit,
                        left + (QUIET + x) * unit, top + (QUIET + y + 1) * unit, paint);
            }
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
        return PixelFormat.OPAQUE;
    }
}
