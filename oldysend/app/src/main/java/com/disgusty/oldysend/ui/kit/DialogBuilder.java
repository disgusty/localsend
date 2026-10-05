package com.disgusty.oldysend.ui.kit;

import android.app.Dialog;
import android.view.View;

/** Collects a dialog description; the style renders it with {@link #show()}. */
public abstract class DialogBuilder {
    public interface ItemListener {
        void onItem(int index);
    }

    public interface MultiListener {
        void onChanged(int index, boolean checked);
    }

    public static final int ITEMS_PLAIN = 0;
    public static final int ITEMS_SINGLE = 1;
    public static final int ITEMS_MULTI = 2;

    protected CharSequence title;
    protected Ic icon;
    protected CharSequence message;
    protected View view;
    protected CharSequence[] items;
    protected int itemsMode;
    protected int checkedItem = -1;
    protected boolean[] checkedItems;
    protected ItemListener itemListener;
    protected MultiListener multiListener;
    protected String positive;
    protected String negative;
    protected String neutral;
    protected Runnable onPositive;
    protected Runnable onNegative;
    protected Runnable onNeutral;
    protected Runnable onDismiss;
    protected boolean cancelable = true;
    /** Buttons do not close the dialog (validation in the callback, call dismiss manually). */
    protected boolean keepOpen;
    protected Dialog dialog;

    public DialogBuilder title(CharSequence t) {
        title = t;
        return this;
    }

    public DialogBuilder icon(Ic i) {
        icon = i;
        return this;
    }

    public DialogBuilder message(CharSequence m) {
        message = m;
        return this;
    }

    public DialogBuilder view(View v) {
        view = v;
        return this;
    }

    public DialogBuilder items(CharSequence[] items, ItemListener l) {
        this.items = items;
        this.itemsMode = ITEMS_PLAIN;
        this.itemListener = l;
        return this;
    }

    /** Radio list; selecting closes the dialog unless positive/negative buttons are set. */
    public DialogBuilder singleChoice(CharSequence[] items, int checked, ItemListener l) {
        this.items = items;
        this.itemsMode = ITEMS_SINGLE;
        this.checkedItem = checked;
        this.itemListener = l;
        return this;
    }

    public DialogBuilder multiChoice(CharSequence[] items, boolean[] checked, MultiListener l) {
        this.items = items;
        this.itemsMode = ITEMS_MULTI;
        this.checkedItems = checked;
        this.multiListener = l;
        return this;
    }

    public DialogBuilder positive(String label, Runnable r) {
        positive = label;
        onPositive = r;
        return this;
    }

    public DialogBuilder negative(String label, Runnable r) {
        negative = label;
        onNegative = r;
        return this;
    }

    public DialogBuilder neutral(String label, Runnable r) {
        neutral = label;
        onNeutral = r;
        return this;
    }

    public DialogBuilder onDismiss(Runnable r) {
        onDismiss = r;
        return this;
    }

    public DialogBuilder cancelable(boolean c) {
        cancelable = c;
        return this;
    }

    public DialogBuilder keepOpen() {
        keepOpen = true;
        return this;
    }

    public abstract Dialog show();

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
                // activity already gone
            }
        }
    }
}
