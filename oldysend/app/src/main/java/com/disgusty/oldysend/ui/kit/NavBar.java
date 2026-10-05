package com.disgusty.oldysend.ui.kit;

import android.view.View;

/** Main navigation (tabs or bottom bar). */
public abstract class NavBar {
    public interface Listener {
        void onSelected(int index);
    }

    protected final String[] labels;
    protected final Ic[] icons;
    protected final Listener listener;
    protected int selected;

    protected NavBar(String[] labels, Ic[] icons, Listener listener) {
        this.labels = labels;
        this.icons = icons;
        this.listener = listener;
    }

    public abstract View view();

    /** True when placed at the bottom of the screen (Material 3 navigation bar). */
    public abstract boolean bottom();

    public void select(int index) {
        selected = index;
        refresh();
    }

    public int selected() {
        return selected;
    }

    protected void clicked(int index) {
        if (index == selected) return;
        select(index);
        if (listener != null) listener.onSelected(index);
    }

    protected abstract void refresh();
}
