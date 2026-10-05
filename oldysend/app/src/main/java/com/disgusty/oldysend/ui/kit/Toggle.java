package com.disgusty.oldysend.ui.kit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Checkable;

/** Switch / checkbox / radio button drawn entirely by a stateful style drawable. */
public class Toggle extends View implements Checkable {
    public interface Listener {
        void onChanged(Toggle toggle, boolean checked);
    }

    private static final int[] CHECKED_STATE = {android.R.attr.state_checked};

    private final Drawable drawable;
    private boolean checked;
    private Listener listener;
    private boolean userToggle = true;

    public Toggle(Context context, Drawable drawable) {
        super(context);
        this.drawable = drawable;
        drawable.setCallback(this);
        setClickable(true);
        setFocusable(true);
    }

    /** Disable when the parent row handles clicks and forwards them via {@link #toggle()}. */
    public Toggle passive() {
        userToggle = false;
        setClickable(false);
        setFocusable(false);
        return this;
    }

    public Toggle listener(Listener l) {
        listener = l;
        return this;
    }

    @Override
    public boolean performClick() {
        if (userToggle) toggle();
        return super.performClick();
    }

    @Override
    public void setChecked(boolean c) {
        if (checked == c) return;
        checked = c;
        refreshDrawableState();
        invalidate();
    }

    /** Sets the state and notifies the listener, as a user interaction would. */
    public void setCheckedByUser(boolean c) {
        if (checked == c) return;
        setChecked(c);
        if (listener != null) listener.onChanged(this, c);
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        setCheckedByUser(!checked);
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        int[] state = super.onCreateDrawableState(extraSpace + 1);
        if (checked) mergeDrawableStates(state, CHECKED_STATE);
        return state;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (drawable.isStateful()) drawable.setState(getDrawableState());
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == drawable || super.verifyDrawable(who);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = drawable.getIntrinsicWidth() + getPaddingLeft() + getPaddingRight();
        int h = drawable.getIntrinsicHeight() + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        int left = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - w) / 2;
        int top = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - h) / 2;
        drawable.setBounds(left, top, left + w, top + h);
        drawable.draw(canvas);
    }
}
