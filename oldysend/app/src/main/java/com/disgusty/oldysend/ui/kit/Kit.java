package com.disgusty.oldysend.ui.kit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.ui.icon.IconDrawable;

import java.util.List;

/**
 * The UI toolkit contract. Screens are written once against this class; each interface style
 * (Classic 2.x, Holo, Material 1, Material 3) implements the widgets the way that era of Android drew them.
 */
public abstract class Kit {
    // Text roles
    public static final int T_DISPLAY = 0;   // big alias on the receive tab
    public static final int T_HEADLINE = 1;  // dialog / page headline
    public static final int T_TITLE = 2;     // section title / large list title
    public static final int T_SUBTITLE = 3;  // list item primary text
    public static final int T_BODY = 4;      // paragraph text
    public static final int T_SECONDARY = 5; // list item secondary text
    public static final int T_CAPTION = 6;   // small hints
    public static final int T_LABEL = 7;     // button-like / chip label
    public static final int T_SECTION = 8;   // section header

    // Button kinds
    public static final int B_PRIMARY = 0;
    public static final int B_SECONDARY = 1;
    public static final int B_TEXT = 2;

    // Toggle kinds
    public static final int SWITCH = 0;
    public static final int CHECKBOX = 1;
    public static final int RADIO = 2;

    public final Activity activity;
    public final Context context;
    public final Scheme c;
    public final float density;
    public final String style;
    protected final boolean animations;

    protected Kit(Activity activity, Scheme scheme) {
        this.activity = activity;
        this.context = activity;
        this.c = scheme;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.style = scheme.style;
        this.animations = Core.get().settings.animations();
    }

    public static Kit create(Activity a) {
        Settings s = Core.get().settings;
        Scheme scheme = Scheme.resolve(a, s);
        String st = scheme.style;
        if (Settings.STYLE_MD3.equals(st)) return new Md3Kit(a, scheme);
        if (Settings.STYLE_MD1.equals(st)) return new Md1Kit(a, scheme);
        if (Settings.STYLE_HOLO.equals(st)) return new HoloKit(a, scheme);
        return new ClassicKit(a, scheme);
    }

    /**
     * True when layouts are mirrored (right-to-left language on Android 4.2+). LinearLayout then places children
     * from the right, so margins and paddings given as left/right have to be swapped by the caller.
     */
    public static boolean rtl() {
        com.disgusty.oldysend.i18n.I18n t = com.disgusty.oldysend.i18n.I18n.get();
        return t != null && t.isRtl() && com.disgusty.oldysend.util.Sdk.atLeast(17);
    }

    public final int dp(float v) {
        return Math.round(v * density);
    }

    // ---------------------------------------------------------------- window & screen

    /** Window background, status/navigation bar colors. Called before the content view is set. */
    public abstract void applyWindow();

    /** The page skeleton: app bar, optional main navigation, content area. */
    public abstract Screen screen();

    public abstract AppBar appBar();

    /**
     * Main navigation between Receive / Send / Settings (Classic: TabWidget, Holo: action bar tabs,
     * Material 1: tabs in the app bar, Material 3: bottom navigation bar).
     */
    public abstract NavBar navBar(String[] labels, Ic[] icons, NavBar.Listener listener);

    // ---------------------------------------------------------------- text

    public abstract Typeface typeface(int role);

    public abstract float textSize(int role);

    public abstract int textColor(int role);

    public TextView text(int role, CharSequence s) {
        TextView t = new TextView(context);
        styleText(t, role);
        t.setText(s);
        return t;
    }

    public void styleText(TextView t, int role) {
        t.setTypeface(typeface(role));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize(role));
        t.setTextColor(textColor(role));
        t.setIncludeFontPadding(true);
    }

    // ---------------------------------------------------------------- layout helpers

    public LinearLayout vbox() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public LinearLayout hbox() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public ScrollView scroll(View content) {
        ScrollView s = new ScrollView(context);
        s.setFillViewport(true);
        s.setVerticalFadingEdgeEnabled(Settings.STYLE_CLASSIC.equals(style) || Settings.STYLE_HOLO.equals(style));
        s.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    public View space(int dp) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dp), dp(dp)));
        return v;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    /** Horizontal padding of page content (list rows use their own). */
    public abstract int pagePadding();

    // ---------------------------------------------------------------- icons

    /** Icon glyph in the style's icon language, tinted with {@code color}. */
    public IconDrawable icon(Ic ic, int color, int sizeDp) {
        return new IconDrawable(Settings.STYLE_MD3.equals(style) ? ic.m3 : ic.m1, dp(sizeDp), color);
    }

    /** Icon for list rows / buttons in the default icon color of the style. */
    public Drawable listIcon(Ic ic) {
        return icon(ic, iconColor(), 24);
    }

    public abstract int iconColor();

    public ImageView image(Drawable d) {
        ImageView iv = new ImageView(context);
        iv.setImageDrawable(d);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return iv;
    }

    /** The app logo drawn in the style (receive tab, about page). */
    public abstract Drawable logo(int sizePx);

    // ---------------------------------------------------------------- lists

    public abstract View sectionHeader(String title);

    public abstract Row row();

    public abstract View divider();

    /** Background used for clickable rows. */
    public abstract Drawable listSelector();

    /** Applies divider and selector of the style to a ListView (history, apps). */
    public abstract void styleList(ListView list);

    // ---------------------------------------------------------------- controls

    public abstract View button(String label, int kind, View.OnClickListener onClick);

    public abstract View iconButton(Ic icon, String description, View.OnClickListener onClick);

    /** A large "picker" button with icon above the label (send tab: File, Folder, Media...). */
    public abstract View tile(Ic icon, String label, View.OnClickListener onClick);

    public abstract Toggle toggle(int kind);

    public abstract Field field(String label, boolean multiline);

    public abstract Progress progress(boolean indeterminate);

    public abstract View spinner();

    public abstract Segmented segmented(String[] labels, Ic[] icons);

    /** A grouped container (Material card; plain panel in Classic/Holo). */
    public abstract ViewGroup card(boolean clickable);

    /** Small rounded label (device model, "Favorite"...). */
    public abstract View chip(String label);

    // ---------------------------------------------------------------- overlays

    public abstract DialogBuilder dialog();

    /** Overflow / context menu anchored at a view. */
    public abstract void menu(View anchor, String title, List<Action> actions);

    public abstract void toast(String message);

    // ---------------------------------------------------------------- shared helpers

    public TextView ellipsize(TextView t, int lines) {
        t.setMaxLines(lines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static EditText edit(Field f) {
        return f.edit;
    }

    /** A Classic-only options panel (2.x icon menu); other styles return false. */
    public boolean onMenuKey(Screen screen) {
        return false;
    }

    /** Window background plus system bar colors (Android 5.0+; light icons from 6.0/8.1). */
    protected void systemBars(int windowBackground, int status, int nav, boolean lightStatus, boolean lightNav) {
        activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(windowBackground));
        if (com.disgusty.oldysend.util.Sdk.atLeast(21)) SystemBars.apply(activity, status, nav, lightStatus, lightNav);
    }

    @android.annotation.TargetApi(21)
    private static final class SystemBars {
        @SuppressWarnings("deprecation")
        static void apply(Activity a, int status, int nav, boolean lightStatus, boolean lightNav) {
            android.view.Window w = a.getWindow();
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(status);
            w.setNavigationBarColor(nav);
            int flags = w.getDecorView().getSystemUiVisibility();
            if (com.disgusty.oldysend.util.Sdk.atLeast(23)) {
                flags = lightStatus ? flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (com.disgusty.oldysend.util.Sdk.atLeast(27)) {
                flags = lightNav ? flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            w.getDecorView().setSystemUiVisibility(flags);
            if (com.disgusty.oldysend.util.Sdk.atLeast(28)) {
                android.view.WindowManager.LayoutParams p = w.getAttributes();
                p.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(p);
            }
        }
    }

    /** Shows a dialog window with a fully custom, transparent-framed content view. */
    protected android.app.Dialog rawDialog(View content, boolean cancelable, float dim, int maxWidthDp) {
        final android.app.Dialog d = new android.app.Dialog(activity);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(content);
        d.setCancelable(cancelable);
        d.setCanceledOnTouchOutside(cancelable);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            android.view.WindowManager.LayoutParams p = w.getAttributes();
            p.dimAmount = dim;
            int screen = activity.getResources().getDisplayMetrics().widthPixels;
            p.width = Math.min(screen - dp(32), dp(maxWidthDp));
            w.setAttributes(p);
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        return d;
    }
}
