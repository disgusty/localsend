package com.disgusty.oldysend.ui.kit;

/** An app bar / menu entry. */
public final class Action {
    public final Ic icon;
    public final String label;
    public final Runnable run;
    /** Shown as an icon in the app bar (Holo/Material) instead of the overflow menu. */
    public boolean always;
    public boolean checkable;
    public boolean checked;
    public boolean enabled = true;

    public Action(Ic icon, String label, Runnable run) {
        this.icon = icon;
        this.label = label;
        this.run = run;
    }

    public Action always() {
        always = true;
        return this;
    }

    public Action checkable(boolean checked) {
        this.checkable = true;
        this.checked = checked;
        return this;
    }

    public Action enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }
}
