package com.disgusty.oldysend.ui.kit;

import android.view.View;

/** Single-choice button group (quick save Off / Favorites / On). */
public abstract class Segmented {
    public interface Listener {
        void onSelected(int index);
    }

    protected Listener listener;
    protected int selected = -1;

    public abstract View view();

    public Segmented listener(Listener l) {
        listener = l;
        return this;
    }

    public void select(int index) {
        selected = index;
        refresh();
    }

    protected void clicked(int index) {
        if (index == selected) return;
        select(index);
        if (listener != null) listener.onSelected(index);
    }

    protected abstract void refresh();
}
