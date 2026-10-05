package com.disgusty.oldysend.ui.kit;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.ui.icon.IconDrawable;

import java.util.ArrayList;
import java.util.List;

/** Material 3 (m3.material.io): small top app bar, navigation bar, tonal surfaces, dynamic color. */
final class Md3Kit extends MaterialKit {
    Md3Kit(Activity a, Scheme s) {
        super(a, s, true);
    }

    @Override
    protected float buttonRadius() {
        return dp(20);
    }

    @Override
    protected float cardRadius() {
        return dp(12);
    }

    @Override
    protected float dialogRadius() {
        return dp(28);
    }

    @Override
    protected float menuRadius() {
        return dp(4);
    }

    @Override
    protected int dialogBackground() {
        return c.surfaceContainerHigh;
    }

    @Override
    protected int menuBackground() {
        return c.surfaceContainer;
    }

    @Override
    protected boolean capsButtons() {
        return false;
    }

    /** M3 type scale (sp). */
    @Override
    public float textSize(int role) {
        switch (role) {
            case T_DISPLAY:
                return 36;  // displaySmall
            case T_HEADLINE:
                return 24;  // headlineSmall
            case T_TITLE:
                return 22;  // titleLarge
            case T_SUBTITLE:
                return 16;  // bodyLarge
            case T_LABEL:
            case T_SECTION:
                return 14;  // labelLarge / titleSmall
            case T_CAPTION:
                return 12;  // bodySmall
            default:
                return 14;  // bodyMedium
        }
    }

    @Override
    public void applyWindow() {
        systemBars(c.background, c.statusBar, c.navigationBar, !c.dark, !c.dark);
    }

    @Override
    public Screen screen() {
        Screen s = new Screen(context, appBar());
        s.root.setBackgroundColor(c.background);
        return s;
    }

    @Override
    public Row row() {
        Row.Metrics m = new Row.Metrics();
        m.minHeightOneLine = dp(56);
        m.minHeightTwoLine = dp(72);
        m.paddingStart = dp(16);
        m.paddingEnd = dp(24);
        m.paddingVertical = dp(8);
        m.iconSize = dp(24);
        m.iconGap = dp(16);
        return new Row(this, m);
    }

    // ---------------------------------------------------------------- top app bar

    @Override
    public AppBar appBar() {
        return new Bar();
    }

    private final class Bar extends AppBar {
        private final LinearLayout root = hbox();
        private final LinearLayout actionsBox = hbox();
        private final FrameLayout navSlot = new FrameLayout(context);
        private final TextView titleView = text(T_TITLE, "");
        private final TextView subtitleView = text(T_CAPTION, "");

        Bar() {
            root.setMinimumHeight(dp(64));
            root.setBackgroundColor(c.appBar);
            root.setPadding(dp(4), 0, dp(4), 0);
            root.addView(navSlot);
            LinearLayout texts = vbox();
            texts.addView(titleView);
            texts.addView(subtitleView);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setVisibility(View.GONE);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            texts.setPadding(dp(12), 0, dp(4), 0);
            root.addView(texts, tp);
            root.addView(actionsBox);
        }

        @Override
        public View view() {
            return root;
        }

        @Override
        public void setScrolled(boolean scrolled) {
            root.setBackgroundColor(scrolled ? c.surfaceContainer : c.appBar);
        }

        @Override
        protected void refresh() {
            titleView.setText(title);
            subtitleView.setText(subtitle == null ? "" : subtitle);
            subtitleView.setVisibility(subtitle == null || subtitle.length() == 0 ? View.GONE : View.VISIBLE);
            navSlot.removeAllViews();
            if (up != null) {
                navSlot.addView(iconButton(Ic.ARROW_BACK, c.onSurface, null, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        up.run();
                    }
                }));
                ((View) titleView.getParent()).setPadding(dp(8), 0, dp(4), 0);
            } else {
                ((View) titleView.getParent()).setPadding(dp(12), 0, dp(4), 0);
            }
            actionsBox.removeAllViews();
            final List<Action> overflow = new ArrayList<Action>();
            for (final Action a : actions) {
                if (a.always && a.icon != null) {
                    View b = iconButton(a.icon, c.onSurfaceVariant, a.label, new View.OnClickListener() {
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
                anchor[0] = iconButton(Ic.MORE_VERT, c.onSurfaceVariant, null, new View.OnClickListener() {
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

    // ---------------------------------------------------------------- navigation bar

    @Override
    public NavBar navBar(String[] labels, Ic[] icons, NavBar.Listener listener) {
        return new Nav(labels, icons, listener);
    }

    /** Active indicator pill that expands horizontally from the center (M3 motion). */
    private final class Pill extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long start;
        boolean active;

        Pill() {
            paint.setColor(c.secondaryContainer);
        }

        void setActive(boolean a, boolean animate) {
            if (a && !active) start = animate ? SystemClock.uptimeMillis() : 0;
            active = a;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            if (!active) return;
            Rect b = getBounds();
            float f = 1;
            if (start > 0) {
                float t = Math.min(1f, (SystemClock.uptimeMillis() - start) / 200f);
                f = 1 - (1 - t) * (1 - t);
                if (t < 1) invalidateSelf();
                else start = 0;
            }
            float w = b.width() * (0.4f + 0.6f * f) / 2;
            float cx = b.exactCenterX();
            canvas.drawRoundRect(new RectF(cx - w, b.top, cx + w, b.bottom), b.height() / 2f, b.height() / 2f, paint);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private final class Nav extends NavBar {
        private final LinearLayout root = hbox();
        private final List<ImageView> iconViews = new ArrayList<ImageView>();
        private final List<Pill> pills = new ArrayList<Pill>();
        private final List<TextView> labelViews = new ArrayList<TextView>();
        private boolean built;

        Nav(String[] labels, Ic[] icons, Listener listener) {
            super(labels, icons, listener);
            root.setBackgroundColor(c.surfaceContainer);
            root.setMinimumHeight(dp(80));
            root.setGravity(Gravity.TOP);
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                LinearLayout item = vbox();
                item.setGravity(Gravity.CENTER_HORIZONTAL);
                item.setPadding(0, dp(12), 0, dp(16));
                Pill pill = new Pill();
                ImageView iv = new ImageView(context);
                iv.setScaleType(ImageView.ScaleType.CENTER);
                iv.setBackgroundDrawable(pill);
                item.addView(iv, new LinearLayout.LayoutParams(dp(64), dp(32)));
                TextView t = text(T_LABEL, labels[i]);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                t.setPadding(0, dp(4), 0, 0);
                item.addView(t);
                item.setClickable(true);
                item.setBackgroundDrawable(Draw.ripple(Scheme.withAlpha(c.onSurface, 0.1f), null, null, true));
                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clicked(index);
                    }
                });
                root.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                iconViews.add(iv);
                pills.add(pill);
                labelViews.add(t);
            }
            refresh();
            built = true;
        }

        @Override
        public View view() {
            return root;
        }

        @Override
        public boolean bottom() {
            return true;
        }

        @Override
        protected void refresh() {
            for (int i = 0; i < iconViews.size(); i++) {
                boolean sel = i == selected;
                Ic ic = icons[i];
                IconDrawable d = new IconDrawable(sel ? ic.m3Filled : ic.m3, dp(24), sel ? c.onSecondaryContainer : c.onSurfaceVariant);
                iconViews.get(i).setImageDrawable(d);
                pills.get(i).setActive(sel, built && animations);
                TextView t = labelViews.get(i);
                t.setTextColor(sel ? c.onSurface : c.onSurfaceVariant);
                t.setTypeface(Fonts.roboto(context, sel ? Fonts.MEDIUM : Fonts.MEDIUM));
            }
        }
    }

    // ---------------------------------------------------------------- segmented button

    @Override
    public Segmented segmented(final String[] labels, final Ic[] icons) {
        return new Segmented() {
            private final LinearLayout root = hbox();
            private final List<TextView> items = new ArrayList<TextView>();

            {
                root.setBackgroundDrawable(Draw.stroke(0, c.outline, Math.max(1, dp(1)), dp(20)));
                root.setPadding(dp(1), dp(1), dp(1), dp(1));
                for (int i = 0; i < labels.length; i++) {
                    final int index = i;
                    if (i > 0) {
                        View sep = new View(context);
                        sep.setBackgroundColor(c.outline);
                        root.addView(sep, new LinearLayout.LayoutParams(Math.max(1, dp(1)), ViewGroup.LayoutParams.MATCH_PARENT));
                    }
                    TextView t = text(T_LABEL, labels[i]);
                    t.setGravity(Gravity.CENTER);
                    t.setSingleLine(true);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setMinHeight(dp(38));
                    t.setPadding(dp(8), 0, dp(8), 0);
                    t.setCompoundDrawablePadding(dp(6));
                    t.setClickable(true);
                    t.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            clicked(index);
                        }
                    });
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
                float r = dp(19);
                for (int i = 0; i < items.size(); i++) {
                    TextView t = items.get(i);
                    boolean sel = i == selected;
                    float[] radii;
                    if (items.size() == 1) radii = new float[]{r, r, r, r, r, r, r, r};
                    else if (i == 0) radii = new float[]{r, r, 0, 0, 0, 0, r, r};
                    else if (i == items.size() - 1) radii = new float[]{0, 0, r, r, r, r, 0, 0};
                    else radii = new float[8];
                    android.graphics.drawable.GradientDrawable bg = Draw.rect(sel ? c.secondaryContainer : 0, 0);
                    bg.setCornerRadii(radii);
                    t.setBackgroundDrawable(touch(c.onSurface, bg, 0));
                    t.setTextColor(sel ? c.onSecondaryContainer : c.onSurface);
                    Drawable check = sel ? icon(Ic.CHECK, c.onSecondaryContainer, 18) : null;
                    if (check != null) check.setBounds(0, 0, dp(18), dp(18));
                    t.setCompoundDrawables(check, null, null, null);
                }
            }
        };
    }
}
