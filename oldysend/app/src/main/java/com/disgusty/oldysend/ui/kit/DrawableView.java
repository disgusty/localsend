package com.disgusty.oldysend.ui.kit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A view that just draws one (possibly animated) drawable over its bounds. */
public class DrawableView extends View {
    private Drawable drawable;
    private final int w;
    private final int h;

    public DrawableView(Context context, Drawable d, int width, int height) {
        super(context);
        this.w = width;
        this.h = height;
        setDrawable(d);
    }

    public void setDrawable(Drawable d) {
        if (drawable != null) drawable.setCallback(null);
        drawable = d;
        if (d != null) d.setCallback(this);
        invalidate();
    }

    public Drawable drawable() {
        return drawable;
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == drawable || super.verifyDrawable(who);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (drawable != null && drawable.isStateful()) drawable.setState(getDrawableState());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mw = w > 0 ? w : (drawable == null ? 0 : drawable.getIntrinsicWidth());
        int mh = h > 0 ? h : (drawable == null ? 0 : drawable.getIntrinsicHeight());
        setMeasuredDimension(resolveSize(Math.max(mw, getSuggestedMinimumWidth()), widthMeasureSpec),
                resolveSize(Math.max(mh, getSuggestedMinimumHeight()), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawable == null) return;
        drawable.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        drawable.draw(canvas);
    }
}
