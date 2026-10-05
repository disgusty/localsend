package com.disgusty.oldysend.ui.kit;

import android.view.View;

/** A horizontal progress indicator. */
public abstract class Progress {
    public abstract View view();

    /** 0..1 */
    public abstract void setProgress(float value);

    public abstract void setIndeterminate(boolean indeterminate);
}
