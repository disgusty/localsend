package com.disgusty.oldysend.ui.kit;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Page skeleton: [app bar][top navigation][content][bottom navigation / button bar]. */
public class Screen {
    public final LinearLayout root;
    public final AppBar appBar;
    protected final FrameLayout content;
    protected final LinearLayout top;
    protected final LinearLayout bottom;
    protected NavBar nav;

    public Screen(Context context, AppBar appBar) {
        this.appBar = appBar;
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        top = new LinearLayout(context);
        top.setOrientation(LinearLayout.VERTICAL);
        content = new FrameLayout(context);
        bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.VERTICAL);
        if (appBar != null) top.addView(appBar.view(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void setNav(NavBar nav) {
        this.nav = nav;
        LinearLayout target = nav.bottom() ? bottom : top;
        target.addView(nav.view(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public NavBar nav() {
        return nav;
    }

    public void setContent(View v) {
        content.removeAllViews();
        content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public FrameLayout content() {
        return content;
    }

    /** Adds a view pinned below the content (button bars of dialogs-as-pages). */
    public void addBottom(View v) {
        bottom.addView(v, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
