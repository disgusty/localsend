package com.disgusty.oldysend.ui.kit;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.util.Sdk;

import java.util.ArrayList;
import java.util.List;

/**
 * Material Design (2014 spec, Android 5.0 Theme.Material): colored Toolbar with 4dp elevation,
 * fixed tabs with an accent indicator, raised/flat buttons, 2dp corners.
 */
final class Md1Kit extends MaterialKit {
    Md1Kit(Activity a, Scheme s) {
        super(a, s, false);
    }

    @Override
    protected float buttonRadius() {
        return dp(2);
    }

    @Override
    protected float cardRadius() {
        return dp(2);
    }

    @Override
    protected float dialogRadius() {
        return dp(2);
    }

    @Override
    protected float menuRadius() {
        return dp(2);
    }

    @Override
    protected int dialogBackground() {
        return c.surface;
    }

    @Override
    protected int menuBackground() {
        return c.surface;
    }

    @Override
    protected boolean capsButtons() {
        return true;
    }

    /** Material 2014 type scale: Display 1 34, Headline 24, Title 20, Subhead 16, Body 14, Caption 12. */
    @Override
    public float textSize(int role) {
        switch (role) {
            case T_DISPLAY:
                return 34;
            case T_HEADLINE:
                return 24;
            case T_TITLE:
                return 20;
            case T_SUBTITLE:
                return 16;
            case T_CAPTION:
                return 12;
            default:
                return 14;
        }
    }

    @Override
    public void applyWindow() {
        systemBars(c.background, c.statusBar, c.navigationBar, false, false);
    }

    @Override
    public Row row() {
        Row.Metrics m = new Row.Metrics();
        m.minHeightOneLine = dp(48);
        m.minHeightTwoLine = dp(72);
        m.paddingStart = dp(16);
        m.paddingEnd = dp(16);
        m.paddingVertical = dp(8);
        m.iconSize = dp(24);
        m.iconGap = dp(32); // text keyline at 72dp
        return new Row(this, m);
    }

    // ---------------------------------------------------------------- screen with elevated, colored top area

    @Override
    public Screen screen() {
        Screen s = new Screen(context, appBar()) {
            private View shadow;

            {
                top.setBackgroundColor(c.appBar);
                if (Sdk.atLeast(21)) {
                    Draw.elevate(top, dp(4), 0);
                } else {
                    // Pre-L toolbars drew a gradient shadow below (support library "ab shadow").
                    shadow = new View(context);
                    shadow.setBackgroundDrawable(Draw.gradient(0x40000000, 0x00000000, 0));
                    content.addView(shadow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP));
                }
            }

            @Override
            public void setContent(View v) {
                super.setContent(v);
                if (shadow != null) {
                    content.removeView(shadow);
                    content.addView(shadow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP));
                }
            }
        };
        s.root.setBackgroundColor(c.background);
        return s;
    }

    // ---------------------------------------------------------------- toolbar

    @Override
    public AppBar appBar() {
        return new Toolbar();
    }

    private final class Toolbar extends AppBar {
        private final LinearLayout root = hbox();
        private final LinearLayout actionsBox = hbox();
        private final FrameLayout navSlot = new FrameLayout(context);
        private final TextView titleView = new TextView(context);
        private final TextView subtitleView = new TextView(context);
        private final LinearLayout texts = vbox();

        Toolbar() {
            root.setMinimumHeight(dp(56));
            root.setBackgroundColor(c.appBar);
            root.addView(navSlot);
            titleView.setTypeface(Fonts.roboto(context, Fonts.MEDIUM));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            titleView.setTextColor(c.onAppBar);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            subtitleView.setTextColor(Scheme.withAlpha(c.onAppBar, 0.7f));
            subtitleView.setSingleLine(true);
            texts.addView(titleView);
            texts.addView(subtitleView);
            root.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            root.addView(actionsBox);
            actionsBox.setPadding(0, 0, dp(4), 0);
        }

        @Override
        public View view() {
            return root;
        }

        @Override
        protected void refresh() {
            titleView.setText(title);
            subtitleView.setText(subtitle == null ? "" : subtitle);
            subtitleView.setVisibility(subtitle == null || subtitle.length() == 0 ? View.GONE : View.VISIBLE);
            navSlot.removeAllViews();
            if (up != null) {
                View b = iconButton(Ic.ARROW_BACK, c.onAppBar, null, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        up.run();
                    }
                });
                b.setLayoutParams(new FrameLayout.LayoutParams(dp(56), dp(56)));
                navSlot.addView(b);
                texts.setPadding(dp(16), 0, dp(8), 0); // title keyline 72dp
            } else {
                texts.setPadding(dp(16), 0, dp(8), 0);
            }
            actionsBox.removeAllViews();
            final List<Action> overflow = new ArrayList<Action>();
            for (final Action a : actions) {
                if (a.always && a.icon != null) {
                    View b = iconButton(a.icon, c.onAppBar, a.label, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (a.run != null) a.run.run();
                        }
                    });
                    b.setEnabled(a.enabled);
                    actionsBox.addView(b);
                } else {
                    overflow.add(a);
                }
            }
            if (!overflow.isEmpty()) {
                final View[] anchor = new View[1];
                anchor[0] = iconButton(Ic.MORE_VERT, c.onAppBar, null, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        menu(anchor[0], null, overflow);
                    }
                });
                actionsBox.addView(anchor[0]);
            }
        }

        @Override
        public void openMenu() {
            int n = actionsBox.getChildCount();
            if (n > 0) actionsBox.getChildAt(n - 1).performClick();
        }
    }

    // ---------------------------------------------------------------- fixed tabs

    @Override
    public NavBar navBar(String[] labels, Ic[] icons, NavBar.Listener listener) {
        return new Tabs(labels, icons, listener);
    }

    private final class Tabs extends NavBar {
        private final FrameLayout root = new FrameLayout(context);
        private final LinearLayout row = hbox();
        private final View indicator = new View(context);
        private final List<TextView> tabs = new ArrayList<TextView>();
        private float indicatorPos = -1;

        Tabs(String[] labels, Ic[] icons, Listener listener) {
            super(labels, icons, listener);
            root.setBackgroundColor(c.appBar);
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                TextView t = new TextView(context);
                t.setText(labels[i].toUpperCase());
                t.setTypeface(Fonts.roboto(context, Fonts.MEDIUM));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                t.setMinHeight(dp(48));
                t.setBackgroundDrawable(Draw.ripple(0x33FFFFFF, null, Draw.rect(0xFFFFFFFF, 0), false));
                t.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clicked(index);
                    }
                });
                trackHotspot(t);
                row.addView(t, new LinearLayout.LayoutParams(0, dp(48), 1));
                tabs.add(t);
            }
            root.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            indicator.setBackgroundColor(c.accent);
            root.addView(indicator, new FrameLayout.LayoutParams(0, dp(2), Gravity.BOTTOM | Gravity.LEFT));
            refresh();
        }

        @Override
        public View view() {
            return root;
        }

        @Override
        public boolean bottom() {
            return false;
        }

        @Override
        protected void refresh() {
            for (int i = 0; i < tabs.size(); i++) {
                tabs.get(i).setTextColor(i == selected ? c.onAppBar : Scheme.withAlpha(c.onAppBar, 0.7f));
            }
            moveIndicator();
        }

        void moveIndicator() {
            int w = root.getWidth();
            if (w == 0 || tabs.isEmpty()) {
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        if (root.getWidth() > 0) moveIndicator();
                    }
                });
                return;
            }
            final int tw = w / tabs.size();
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) indicator.getLayoutParams();
            p.width = tw;
            indicator.setLayoutParams(p);
            // The indicator is placed from the left edge; mirrored tab rows count from the right.
            final float target = tw * (rtl() ? tabs.size() - 1 - selected : selected);
            if (indicatorPos < 0 || !animations) {
                indicatorPos = target;
                setIndicatorX(target);
                return;
            }
            final float from = indicatorPos;
            indicatorPos = target;
            final long start = android.os.SystemClock.uptimeMillis();
            root.post(new Runnable() {
                @Override
                public void run() {
                    float t = Math.min(1f, (android.os.SystemClock.uptimeMillis() - start) / 250f);
                    float e = 1 - (1 - t) * (1 - t);
                    setIndicatorX(from + (target - from) * e);
                    if (t < 1) root.post(this);
                }
            });
        }

        private void setIndicatorX(float x) {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) indicator.getLayoutParams();
            p.leftMargin = Math.round(x);
            indicator.setLayoutParams(p);
        }
    }

    @Override
    public Segmented segmented(final String[] labels, final Ic[] icons) {
        // Material toggle button group: 1dp divider-colored border, selected button tinted.
        return new Segmented() {
            private final LinearLayout root = hbox();
            private final List<TextView> items = new ArrayList<TextView>();

            {
                root.setBackgroundDrawable(Draw.stroke(c.surface, c.divider, Math.max(1, dp(1)), dp(2)));
                root.setPadding(dp(1), dp(1), dp(1), dp(1));
                for (int i = 0; i < labels.length; i++) {
                    final int index = i;
                    if (i > 0) {
                        View sep = new View(context);
                        sep.setBackgroundColor(c.divider);
                        root.addView(sep, new LinearLayout.LayoutParams(Math.max(1, dp(1)), ViewGroup.LayoutParams.MATCH_PARENT));
                    }
                    TextView t = text(T_LABEL, labels[i].toUpperCase());
                    t.setGravity(Gravity.CENTER);
                    t.setSingleLine(true);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setMinHeight(dp(40));
                    t.setPadding(dp(8), 0, dp(8), 0);
                    t.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            clicked(index);
                        }
                    });
                    trackHotspot(t);
                    root.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    items.add(t);
                }
                refresh();
            }

            @Override
            public View view() {
                return root;
            }

            @Override
            protected void refresh() {
                for (int i = 0; i < items.size(); i++) {
                    boolean sel = i == selected;
                    TextView t = items.get(i);
                    Drawable bg = Draw.rect(sel ? (c.dark ? 0x33FFFFFF : 0x1F000000) : 0, 0);
                    t.setBackgroundDrawable(touch(c.onSurface, bg, 0));
                    t.setTextColor(sel ? c.accent : c.onSurfaceVariant);
                }
            }
        };
    }
}
