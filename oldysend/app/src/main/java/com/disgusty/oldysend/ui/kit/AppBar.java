package com.disgusty.oldysend.ui.kit;

import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Title bar / action bar / toolbar / top app bar, depending on the style. */
public abstract class AppBar {
    protected CharSequence title = "";
    protected CharSequence subtitle;
    protected Runnable up;
    protected final List<Action> actions = new ArrayList<Action>();

    public abstract View view();

    public AppBar title(CharSequence title) {
        this.title = title;
        refresh();
        return this;
    }

    public AppBar subtitle(CharSequence subtitle) {
        this.subtitle = subtitle;
        refresh();
        return this;
    }

    /** Shows the up/back affordance (Holo up caret, Material back arrow); Classic ignores it (hardware back). */
    public AppBar up(Runnable up) {
        this.up = up;
        refresh();
        return this;
    }

    public AppBar actions(List<Action> list) {
        actions.clear();
        if (list != null) actions.addAll(list);
        refresh();
        return this;
    }

    public List<Action> actions() {
        return actions;
    }

    /** Opens the overflow / options menu (menu key). */
    public abstract void openMenu();

    protected abstract void refresh();

    /** Material 3 tints the bar when content scrolls underneath. */
    public void setScrolled(boolean scrolled) {
    }
}
