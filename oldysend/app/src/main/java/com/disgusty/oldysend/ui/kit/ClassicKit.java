package com.disgusty.oldysend.ui.kit;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.disgusty.oldysend.R;
import com.disgusty.oldysend.ui.icon.IconDrawable;
import com.disgusty.oldysend.util.Sdk;

import java.util.ArrayList;
import java.util.List;

/**
 * Android 1.x/2.x look ("Classic", pre-Holo), built from the Android 2.3.7 framework resources:
 * grey window title bar, TabWidget, btn_default 9-patches, orange list highlight, popup_* AlertDialog panels,
 * the icon options menu on the Menu key, the yellow horizontal progress bar and Droid Sans.
 */
final class ClassicKit extends Kit {
    private final boolean dark;

    ClassicKit(Activity a, Scheme s) {
        super(a, s);
        dark = s.dark;
    }

    @SuppressWarnings("deprecation")
    private Drawable res(int id) {
        return activity.getResources().getDrawable(id);
    }

    // ---------------------------------------------------------------- text

    @Override
    public Typeface typeface(int role) {
        boolean bold = role == T_SECTION || role == T_HEADLINE;
        return Fonts.classic(context, bold ? Fonts.BOLD : Fonts.REGULAR);
    }

    /** TextAppearance.Large 22sp, Medium 18sp, Small 14sp (Android 2.3 styles.xml). */
    @Override
    public float textSize(int role) {
        switch (role) {
            case T_DISPLAY:
                return 30;
            case T_HEADLINE:
            case T_TITLE:
            case T_SUBTITLE:
                return 22;
            case T_BODY:
                return 18;
            case T_LABEL:
            case T_SECONDARY:
            case T_SECTION:
                return 14;
            default:
                return 14;
        }
    }

    @Override
    public int textColor(int role) {
        if (role == -1) return c.textDisabled;
        if (role == T_SECONDARY || role == T_CAPTION || role == T_SECTION) return c.onSurfaceVariant;
        return c.onSurface;
    }

    @Override
    public int iconColor() {
        return dark ? 0xFFBEBEBE : 0xFF555555;
    }

    @Override
    public int pagePadding() {
        return dp(10);
    }

    /** Android 2.x icons: grey vertical gradient glyphs with a dark drop shadow. */
    @Override
    public IconDrawable icon(Ic ic, int color, int sizeDp) {
        IconDrawable d = super.icon(ic, color, sizeDp);
        int px = dp(sizeDp);
        int top = Scheme.blend(color, 0xFFFFFFFF, 0.35f);
        int bottom = Scheme.blend(color, 0xFF000000, 0.25f);
        d.setShader(Draw.vertical(px, top, bottom));
        d.setShadow(0, 0, Math.max(1, dp(1)), dark ? 0xAA000000 : 0x55000000);
        return d;
    }

    @Override
    public Drawable logo(int sizePx) {
        // Glossy, gradient-filled launcher icon of the 2.x icon guidelines.
        return new LogoDrawable(sizePx, 0xFF5A5A5A, 0xFFFFA200, 0xFFFFFFFF)
                .shader(Draw.radial(sizePx * 0.5f, sizePx * 0.3f, sizePx * 0.7f, 0xFFFFC94D, 0xFFE07B00)).gloss();
    }

    // ---------------------------------------------------------------- window

    @Override
    public void applyWindow() {
        systemBars(c.background, 0xFF000000, 0xFF000000, false, false);
    }

    @Override
    public Screen screen() {
        Screen s = new Screen(context, appBar());
        s.root.setBackgroundColor(c.background);
        return s;
    }

    @Override
    public AppBar appBar() {
        return new TitleBar();
    }

    /** The 25dip window title bar (WindowTitleBackground + TextAppearance.WindowTitle: 14sp bold white, shadow). */
    private final class TitleBar extends AppBar {
        private final LinearLayout root = hbox();
        private final TextView titleView = new TextView(context);
        private View menuButton;

        TitleBar() {
            root.setBackgroundDrawable(res(R.drawable.cl_activity_title_bar));
            root.setMinimumHeight(dp(25));
            root.setPadding(dp(10), 0, dp(4), 0);
            titleView.setTypeface(Fonts.classic(context, Fonts.BOLD));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            titleView.setTextColor(0xFFFFFFFF);
            titleView.setShadowLayer(2.75f, 0, 0, 0xBB000000);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }

        @Override
        public View view() {
            return root;
        }

        @Override
        protected void refresh() {
            titleView.setText(subtitle != null && subtitle.length() > 0 ? title + " — " + subtitle : title);
            if (menuButton != null) {
                root.removeView(menuButton);
                menuButton = null;
            }
            // 2.x apps put actions in the Menu-key panel. Devices without a menu key get a small button.
            if (!actions.isEmpty() && !hasMenuKey()) {
                ImageView iv = new ImageView(context);
                iv.setImageDrawable(res(R.drawable.cl_ic_menu_more));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setPadding(dp(4), dp(2), dp(4), dp(2));
                iv.setBackgroundDrawable(listSelector());
                iv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openMenu();
                    }
                });
                menuButton = iv;
                root.addView(iv, new LinearLayout.LayoutParams(dp(32), dp(25)));
            }
        }

        @Override
        public void openMenu() {
            iconMenu(actions);
        }
    }

    private boolean hasMenuKey() {
        if (!Sdk.atLeast(14)) return true;
        return Api14.hasPermanentMenuKey(activity);
    }

    @android.annotation.TargetApi(14)
    private static final class Api14 {
        static boolean hasPermanentMenuKey(Activity a) {
            return ViewConfiguration.get(a).hasPermanentMenuKey();
        }
    }

    @Override
    public boolean onMenuKey(Screen screen) {
        if (screen.appBar == null || screen.appBar.actions().isEmpty()) return false;
        iconMenu(screen.appBar.actions());
        return true;
    }

    /** The 2.x options menu: icon grid at the bottom of the screen (menu_background_fill_parent_width). */
    private void iconMenu(List<Action> actions) {
        LinearLayout panel = vbox();
        panel.setBackgroundDrawable(res(R.drawable.cl_menu_background_fill_parent_width));
        final PopupWindow[] holder = new PopupWindow[1];
        int perRow = actions.size() <= 3 ? actions.size() : (actions.size() == 4 ? 2 : 3);
        LinearLayout row = null;
        for (int i = 0; i < actions.size(); i++) {
            final Action a = actions.get(i);
            if (i % perRow == 0) {
                row = hbox();
                panel.addView(row, matchWrap());
            } else {
                ImageView sep = new ImageView(context);
                sep.setBackgroundDrawable(res(R.drawable.cl_divider_vertical_dark));
                row.addView(sep, new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(65)));
            }
            LinearLayout item = vbox();
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(dp(65));
            item.setPadding(dp(4), dp(6), dp(4), dp(6));
            ImageView iv = new ImageView(context);
            iv.setImageDrawable(menuIcon(a.icon));
            item.addView(iv, new LinearLayout.LayoutParams(dp(32), dp(32)));
            TextView t = new TextView(context);
            t.setText(a.checkable ? (a.checked ? "✓ " : "") + a.label : a.label);
            t.setTypeface(Fonts.classic(context, Fonts.REGULAR));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            t.setTextColor(a.enabled ? 0xFFFFFFFF : 0xFF808080);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(t, matchWrap());
            item.setBackgroundDrawable(Draw.states(res(R.drawable.cl_highlight_pressed), res(R.drawable.cl_highlight_selected), null, null, null));
            item.setClickable(a.enabled);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (holder[0] != null) holder[0].dismiss();
                    if (a.run != null) a.run.run();
                }
            });
            row.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        PopupWindow pw = new PopupWindow(panel, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        com.disgusty.oldysend.ui.Compat.outsideTouchable(pw);
        if (Sdk.atLeast(5)) Api5.slideAnimation(pw);
        holder[0] = pw;
        pw.showAtLocation(activity.getWindow().getDecorView(), Gravity.BOTTOM, 0, 0);
    }

    @android.annotation.TargetApi(5)
    private static final class Api5 {
        static void slideAnimation(PopupWindow pw) {
            pw.setAnimationStyle(android.R.style.Animation_InputMethod);
        }
    }

    /** Original 2.x ic_menu_* artwork where AOSP has a matching icon. */
    private Drawable menuIcon(Ic ic) {
        int id = 0;
        if (ic == Ic.SYNC || ic == Ic.REFRESH) id = R.drawable.cl_ic_menu_refresh;
        else if (ic == Ic.HISTORY) id = R.drawable.cl_ic_menu_recent_history;
        else if (ic == Ic.INFO) id = R.drawable.cl_ic_menu_info_details;
        else if (ic == Ic.DELETE || ic == Ic.CLEAR_ALL) id = R.drawable.cl_ic_menu_delete;
        else if (ic == Ic.SETTINGS || ic == Ic.TUNE) id = R.drawable.cl_ic_menu_preferences;
        else if (ic == Ic.SEND) id = R.drawable.cl_ic_menu_send;
        else if (ic == Ic.LINK || ic == Ic.SHARE) id = R.drawable.cl_ic_menu_share;
        else if (ic == Ic.HELP) id = R.drawable.cl_ic_menu_help;
        else if (ic == Ic.ADD || ic == Ic.CREATE_NEW_FOLDER) id = R.drawable.cl_ic_menu_add;
        else if (ic == Ic.EDIT) id = R.drawable.cl_ic_menu_edit;
        else if (ic == Ic.FAVORITE || ic == Ic.STAR) id = R.drawable.cl_ic_menu_star;
        else if (ic == Ic.CLOSE || ic == Ic.CANCEL) id = R.drawable.cl_ic_menu_close_clear_cancel;
        else if (ic == Ic.OPEN_IN_NEW || ic == Ic.FOLDER_OPEN || ic == Ic.VISIBILITY) id = R.drawable.cl_ic_menu_view;
        else if (ic == Ic.SAVE_ALT) id = R.drawable.cl_ic_menu_save;
        else if (ic == Ic.UPLOAD) id = R.drawable.cl_ic_menu_upload;
        else if (ic == Ic.CONTENT_COPY) id = R.drawable.cl_ic_menu_agenda;
        else if (ic == Ic.ARROW_BACK) id = R.drawable.cl_ic_menu_back;
        else if (ic == Ic.STOP) id = R.drawable.cl_ic_menu_stop;
        else if (ic == Ic.MORE_VERT) id = R.drawable.cl_ic_menu_more;
        else if (ic == Ic.PHOTO_LIBRARY || ic == Ic.IMAGE) id = R.drawable.cl_ic_menu_gallery;
        if (id != 0) return res(id);
        if (ic == null) return res(R.drawable.cl_ic_menu_manage);
        // ic_menu_* style for icons AOSP lacks: light grey gradient glyph on the dark panel.
        IconDrawable d = new IconDrawable(ic.m1, dp(32), 0xFFCCCCCC);
        d.setPaddingFraction(0.1f);
        d.setShader(Draw.vertical(dp(32), 0xFFF0F0F0, 0xFF9A9A9A));
        d.setShadow(0, 0, dp(1), 0xCC000000);
        return d;
    }

    // ---------------------------------------------------------------- tabs (TabWidget with tab_indicator_v4)

    @Override
    public NavBar navBar(String[] labels, Ic[] icons, NavBar.Listener listener) {
        return new Tabs(labels, icons, listener);
    }

    private final class Tabs extends NavBar {
        private final LinearLayout root = hbox();
        private final List<TextView> tabs = new ArrayList<TextView>();

        Tabs(String[] labels, Ic[] icons, Listener listener) {
            super(labels, icons, listener);
            root.setBackgroundColor(0xFF000000);
            root.setPadding(0, dp(1), 0, 0);
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                TextView t = new TextView(context);
                t.setText(labels[i]);
                t.setTypeface(Fonts.classic(context, Fonts.REGULAR));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setCompoundDrawablePadding(dp(2));
                t.setPadding(dp(4), dp(6), dp(4), dp(4));
                t.setBackgroundDrawable(tabBackground());
                t.setClickable(true);
                t.setFocusable(true);
                t.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clicked(index);
                    }
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(64), 1);
                if (i > 0) p.leftMargin = dp(-1);
                root.addView(t, p);
                tabs.add(t);
            }
            refresh();
        }

        private Drawable tabBackground() {
            android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
            s.addState(Draw.PRESSED, res(R.drawable.cl_tab_press));
            s.addState(new int[]{android.R.attr.state_focused}, res(R.drawable.cl_tab_focus));
            s.addState(Draw.SELECTED, res(R.drawable.cl_tab_selected_v4));
            s.addState(Draw.ANY, res(R.drawable.cl_tab_unselected_v4));
            return s;
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
                TextView t = tabs.get(i);
                boolean sel = i == selected;
                t.setSelected(sel);
                // tab_indicator_text_v4 (TabHost pairs it with the _v4 tab artwork): #808080 selected, white otherwise.
                t.setTextColor(sel ? 0xFF808080 : 0xFFFFFFFF);
                IconDrawable icon = new IconDrawable(icons[i].m1, dp(32), sel ? 0xFF505050 : 0xFFFFFFFF);
                icon.setShader(sel ? Draw.vertical(dp(32), 0xFF6A6A6A, 0xFF383838) : Draw.vertical(dp(32), 0xFFFFFFFF, 0xFFC8C8C8));
                icon.setShadow(0, 0, dp(1), sel ? 0x66FFFFFF : 0xCC000000);
                icon.setBounds(0, 0, dp(32), dp(32));
                t.setCompoundDrawables(null, icon, null, null);
            }
        }
    }

    // ---------------------------------------------------------------- lists

    @Override
    public Row row() {
        Row.Metrics m = new Row.Metrics();
        m.minHeightOneLine = dp(64); // ?android:attr/listPreferredItemHeight
        m.minHeightTwoLine = dp(64);
        m.paddingStart = dp(10);
        m.paddingEnd = dp(10);
        m.paddingVertical = dp(6);
        m.iconSize = dp(32);
        m.iconGap = dp(10);
        m.titleRole = T_SUBTITLE;
        m.summaryRole = T_SECONDARY;
        return new Row(this, m);
    }

    /** list_selector_background: orange pressed 9-patch, focus 9-patch. */
    @Override
    public Drawable listSelector() {
        return Draw.states(res(R.drawable.cl_list_selector_background_pressed), res(R.drawable.cl_list_selector_background_focus),
                null, null, null);
    }

    @Override
    public void styleList(ListView list) {
        list.setDivider(res(dark ? R.drawable.cl_divider_horizontal_dark : R.drawable.cl_divider_horizontal_bright));
        list.setSelector(listSelector());
        list.setCacheColorHint(c.background);
        list.setBackgroundColor(c.background);
    }

    @Override
    public View divider() {
        ImageView v = new ImageView(context);
        v.setBackgroundDrawable(res(dark ? R.drawable.cl_divider_horizontal_dark : R.drawable.cl_divider_horizontal_bright));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return v;
    }

    /** Widget.TextView.ListSeparator: dark_header_dither, 25dip, bold 14sp, paddingLeft 5sp. */
    @Override
    public View sectionHeader(String title) {
        TextView t = new TextView(context);
        t.setText(title);
        t.setTypeface(Fonts.classic(context, Fonts.BOLD));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(dark ? 0xFFBEBEBE : 0xFF323232);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(25));
        t.setPadding(dp(5), 0, dp(5), 0);
        t.setBackgroundDrawable(res(dark ? R.drawable.cl_dark_header : R.drawable.cl_light_header));
        return t;
    }

    // ---------------------------------------------------------------- controls

    private Drawable btnDefault() {
        return Draw.states(res(R.drawable.cl_btn_default_pressed), res(R.drawable.cl_btn_default_selected), null,
                res(R.drawable.cl_btn_default_normal_disable), res(R.drawable.cl_btn_default_normal));
    }

    private Drawable btnSmall() {
        return Draw.states(res(R.drawable.cl_btn_default_small_pressed), res(R.drawable.cl_btn_default_small_selected), null,
                res(R.drawable.cl_btn_default_small_normal_disable), res(R.drawable.cl_btn_default_small_normal));
    }

    /** Widget.Button: btn_default, black text (primary_text_light). */
    @Override
    public View button(String label, int kind, View.OnClickListener onClick) {
        TextView b = new TextView(context);
        b.setText(label);
        b.setTypeface(Fonts.classic(context, Fonts.REGULAR));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, kind == B_TEXT ? 14 : 16);
        b.setTextColor(0xFF000000);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setBackgroundDrawable(kind == B_TEXT ? btnSmall() : btnDefault());
        b.setMinWidth(dp(kind == B_TEXT ? 48 : 88));
        b.setMinHeight(dp(kind == B_TEXT ? 40 : 48));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        return b;
    }

    @Override
    public View iconButton(Ic icon, String description, View.OnClickListener onClick) {
        ImageView iv = new ImageView(context);
        iv.setImageDrawable(icon(icon, 0xFF4A4A4A, 24));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setBackgroundDrawable(btnSmall());
        iv.setClickable(true);
        iv.setFocusable(true);
        iv.setOnClickListener(onClick);
        if (description != null) com.disgusty.oldysend.ui.Compat.description(iv, description);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(48), dp(44));
        p.leftMargin = dp(2);
        iv.setLayoutParams(p);
        return iv;
    }

    @Override
    public View tile(Ic icon, String label, View.OnClickListener onClick) {
        TextView t = new TextView(context);
        t.setText(label);
        t.setTypeface(Fonts.classic(context, Fonts.REGULAR));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(0xFF000000);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        IconDrawable d = icon(icon, 0xFF5A5A5A, 32);
        d.setBounds(0, 0, dp(32), dp(32));
        t.setCompoundDrawables(null, d, null, null);
        t.setCompoundDrawablePadding(dp(2));
        t.setBackgroundDrawable(btnDefault());
        t.setMinHeight(dp(80));
        t.setClickable(true);
        t.setOnClickListener(onClick);
        return t;
    }

    @Override
    public Toggle toggle(int kind) {
        if (kind == RADIO) {
            return new Toggle(context, radioDrawable());
        }
        // Android 2.x has no switch widget: settings used check boxes.
        return new Toggle(context, checkDrawable());
    }

    private Drawable checkDrawable() {
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_checked, android.R.attr.state_pressed}, res(R.drawable.cl_btn_check_on_pressed));
        s.addState(new int[]{android.R.attr.state_checked, -android.R.attr.state_enabled}, res(R.drawable.cl_btn_check_on_disable));
        s.addState(new int[]{android.R.attr.state_checked}, res(R.drawable.cl_btn_check_on));
        s.addState(new int[]{android.R.attr.state_pressed}, res(R.drawable.cl_btn_check_off_pressed));
        s.addState(new int[]{-android.R.attr.state_enabled}, res(R.drawable.cl_btn_check_off_disable));
        s.addState(Draw.ANY, res(R.drawable.cl_btn_check_off));
        return s;
    }

    private Drawable radioDrawable() {
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_checked, android.R.attr.state_pressed}, res(R.drawable.cl_btn_radio_on_pressed));
        s.addState(new int[]{android.R.attr.state_checked}, res(R.drawable.cl_btn_radio_on));
        s.addState(new int[]{android.R.attr.state_pressed}, res(R.drawable.cl_btn_radio_off_pressed));
        s.addState(Draw.ANY, res(R.drawable.cl_btn_radio_off));
        return s;
    }

    /** Widget.EditText: textfield_* 9-patches, black 18sp text. */
    @Override
    public Field field(String label, boolean multiline) {
        LinearLayout box = vbox();
        if (label != null && label.length() > 0) {
            TextView l = text(T_SECONDARY, label);
            l.setPadding(dp(2), 0, 0, dp(2));
            box.addView(l);
        }
        EditText e = new EditText(context);
        e.setBackgroundDrawable(Draw.states(res(R.drawable.cl_textfield_pressed), res(R.drawable.cl_textfield_selected), null,
                res(R.drawable.cl_textfield_disabled), res(R.drawable.cl_textfield_default)));
        e.setTypeface(Fonts.classic(context, Fonts.REGULAR));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        e.setTextColor(0xFF000000);
        e.setHintTextColor(0xFF808080);
        if (multiline) {
            e.setMinLines(3);
            e.setMaxLines(8);
            e.setGravity(Gravity.TOP | Gravity.LEFT);
        } else {
            e.setSingleLine(true);
        }
        box.addView(e, matchWrap());
        TextView error = text(T_CAPTION, "");
        error.setTextColor(0xFFFF4444);
        error.setVisibility(View.GONE);
        box.addView(error);
        return new Field(box, e, error);
    }

    /** progress_horizontal.xml (yellow gradient, 5dip corners) and progress_indeterminate_horizontal frames. */
    @Override
    public Progress progress(boolean indeterminate) {
        final GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF9D9E9D, 0xFF5A5D5A, 0xFF747674});
        bg.setCornerRadius(dp(5));
        GradientDrawable fg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFD300, 0xFFFFB600, 0xFFFFCB00});
        fg.setCornerRadius(dp(5));
        final ClipDrawable clip = new ClipDrawable(fg, Gravity.LEFT, ClipDrawable.HORIZONTAL);
        final LayerDrawable layers = new LayerDrawable(new Drawable[]{bg, clip});
        final AnimationDrawable anim = new AnimationDrawable();
        anim.addFrame(res(R.drawable.cl_progressbar_indeterminate1), 200);
        anim.addFrame(res(R.drawable.cl_progressbar_indeterminate2), 200);
        anim.addFrame(res(R.drawable.cl_progressbar_indeterminate3), 200);
        anim.setOneShot(false);
        final TiledProgressView v = new TiledProgressView(layers, anim);
        v.setIndeterminate(indeterminate);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(indeterminate ? 20 : 12)));
        return new Progress() {
            @Override
            public View view() {
                return v;
            }

            @Override
            public void setProgress(float value) {
                clip.setLevel(Math.round(Math.max(0, Math.min(1, value)) * 10000));
                v.invalidate();
            }

            @Override
            public void setIndeterminate(boolean ind) {
                v.setIndeterminate(ind);
            }
        };
    }

    private final class TiledProgressView extends View {
        private final Drawable determinate;
        private final AnimationDrawable frames;
        private boolean indeterminate;
        private final long start = SystemClock.uptimeMillis();

        TiledProgressView(Drawable determinate, AnimationDrawable frames) {
            super(context);
            this.determinate = determinate;
            this.frames = frames;
        }

        void setIndeterminate(boolean ind) {
            indeterminate = ind;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!indeterminate) {
                determinate.setBounds(0, 0, getWidth(), getHeight());
                determinate.draw(canvas);
                return;
            }
            int frame = (int) (((SystemClock.uptimeMillis() - start) / 200) % 3);
            Drawable d = frames.getFrame(frame);
            int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : getHeight();
            for (int x = 0; x < getWidth(); x += w) {
                d.setBounds(x, 0, x + w, getHeight());
                d.draw(canvas);
            }
            postInvalidateDelayed(100);
        }
    }

    /** progress_medium_white: spinner_white_48 rotated in 12 steps per second. */
    @Override
    public View spinner() {
        final Drawable d = res(dark ? R.drawable.cl_spinner_white_48 : R.drawable.cl_spinner_black_48);
        final int size = dp(48);
        return new View(context) {
            private final long start = SystemClock.uptimeMillis();

            @Override
            protected void onMeasure(int w, int h) {
                setMeasuredDimension(size, size);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int step = (int) (((SystemClock.uptimeMillis() - start) / 83) % 12);
                canvas.save();
                canvas.rotate(step * 30, size / 2f, size / 2f);
                d.setBounds(0, 0, size, size);
                d.draw(canvas);
                canvas.restore();
                postInvalidateDelayed(83);
            }
        };
    }

    /** ToggleButtons (btn_default + green/grey on-off indicator) for the single-choice group. */
    @Override
    public Segmented segmented(final String[] labels, Ic[] icons) {
        return new Segmented() {
            private final LinearLayout root = hbox();
            private final List<TextView> items = new ArrayList<TextView>();

            {
                for (int i = 0; i < labels.length; i++) {
                    final int index = i;
                    TextView t = new TextView(context);
                    t.setText(labels[i]);
                    t.setTypeface(Fonts.classic(context, Fonts.REGULAR));
                    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    t.setTextColor(0xFF000000);
                    t.setGravity(Gravity.CENTER);
                    t.setSingleLine(true);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setBackgroundDrawable(btnDefault());
                    t.setMinHeight(dp(56));
                    t.setCompoundDrawablePadding(dp(2));
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
                for (int i = 0; i < items.size(); i++) {
                    Drawable ind = res(i == selected ? R.drawable.cl_button_onoff_indicator_on : R.drawable.cl_button_onoff_indicator_off);
                    ind.setBounds(0, 0, ind.getIntrinsicWidth(), ind.getIntrinsicHeight());
                    items.get(i).setCompoundDrawables(null, null, null, ind);
                }
            }
        };
    }

    /** Classic Android had no cards: a plain group. */
    @Override
    public ViewGroup card(boolean clickable) {
        LinearLayout l = vbox();
        if (clickable) {
            l.setBackgroundDrawable(listSelector());
            l.setClickable(true);
        }
        return l;
    }

    @Override
    public View chip(String label) {
        TextView t = new TextView(context);
        t.setText(label);
        t.setTypeface(Fonts.classic(context, Fonts.REGULAR));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(dark ? 0xFFDDDDDD : 0xFF333333);
        t.setSingleLine(true);
        t.setBackgroundDrawable(Draw.stroke(dark ? 0xFF333333 : 0xFFEEEEEE, dark ? 0xFF555555 : 0xFFBBBBBB, 1, dp(3)));
        t.setPadding(dp(5), dp(1), dp(5), dp(1));
        return t;
    }

    // ---------------------------------------------------------------- dialogs (AlertDialog with popup_* panels)

    @Override
    public DialogBuilder dialog() {
        return new DialogBuilder() {
            @Override
            public Dialog show() {
                return showDialog(this);
            }
        };
    }

    private Dialog showDialog(final DialogBuilder b) {
        LinearLayout root = vbox();
        List<View> parts = new ArrayList<View>();
        List<Boolean> bright = new ArrayList<Boolean>();
        if (b.title != null) {
            LinearLayout title = hbox();
            title.setPadding(dp(10), dp(9), dp(10), dp(9));
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(res(b.icon == Ic.WARNING || b.icon == Ic.ERROR ? R.drawable.cl_ic_dialog_alert : R.drawable.cl_ic_dialog_info));
            title.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
            TextView tt = new TextView(context);
            tt.setText(b.title);
            tt.setTypeface(Fonts.classic(context, Fonts.REGULAR));
            tt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            tt.setTextColor(0xFFFFFFFF);
            tt.setSingleLine(true);
            tt.setEllipsize(TextUtils.TruncateAt.END);
            tt.setPadding(dp(8), 0, 0, 0);
            title.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            parts.add(title);
            bright.add(false);
        }
        if (b.message != null || b.view != null) {
            LinearLayout content = vbox();
            content.setPadding(dp(14), dp(5), dp(10), dp(5));
            if (b.message != null) {
                TextView m = new TextView(context);
                m.setText(b.message);
                m.setTypeface(Fonts.classic(context, Fonts.REGULAR));
                m.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                m.setTextColor(0xFFFFFFFF);
                m.setPadding(dp(5), dp(5), dp(5), dp(5));
                content.addView(m);
            }
            if (b.view != null) content.addView(b.view, matchWrap());
            ScrollView sv = new ScrollView(context);
            sv.addView(content);
            parts.add(sv);
            bright.add(false);
        }
        if (b.items != null) {
            // select_dialog_item / singlechoice / multichoice on the bright panel.
            LinearLayout list = vbox();
            for (int i = 0; i < b.items.length; i++) {
                final int index = i;
                LinearLayout item = hbox();
                item.setMinimumHeight(dp(64));
                item.setPadding(dp(12), 0, dp(10), 0);
                TextView t = new TextView(context);
                t.setText(b.items[i]);
                t.setTypeface(Fonts.classic(context, Fonts.REGULAR));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                t.setTextColor(0xFF000000);
                item.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                Toggle tg = null;
                if (b.itemsMode == DialogBuilder.ITEMS_SINGLE) {
                    tg = new Toggle(context, radioDrawable()).passive();
                    tg.setChecked(i == b.checkedItem);
                    item.addView(tg);
                } else if (b.itemsMode == DialogBuilder.ITEMS_MULTI) {
                    tg = new Toggle(context, checkDrawable()).passive();
                    tg.setChecked(b.checkedItems[i]);
                    item.addView(tg);
                }
                item.setBackgroundDrawable(listSelector());
                item.setClickable(true);
                final Toggle toggle = tg;
                final LinearLayout listRef = list;
                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (b.itemsMode == DialogBuilder.ITEMS_MULTI) {
                            toggle.toggle();
                            b.checkedItems[index] = toggle.isChecked();
                            if (b.multiListener != null) b.multiListener.onChanged(index, toggle.isChecked());
                            return;
                        }
                        if (b.itemsMode == DialogBuilder.ITEMS_SINGLE) {
                            b.checkedItem = index;
                            for (int j = 0; j < listRef.getChildCount(); j++) {
                                View row = listRef.getChildAt(j);
                                if (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 1
                                        && ((ViewGroup) row).getChildAt(1) instanceof Toggle) {
                                    ((Toggle) ((ViewGroup) row).getChildAt(1)).setChecked(j / 2 == index);
                                }
                            }
                            if (b.positive != null) return;
                        }
                        if (b.itemListener != null) b.itemListener.onItem(index);
                        b.dismiss();
                    }
                });
                list.addView(item, matchWrap());
                if (i < b.items.length - 1) {
                    ImageView div = new ImageView(context);
                    div.setBackgroundDrawable(res(R.drawable.cl_divider_horizontal_bright));
                    list.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
                }
            }
            ScrollView sv = new ScrollView(context);
            sv.addView(list);
            parts.add(sv);
            bright.add(true);
        }
        if (b.positive != null || b.negative != null || b.neutral != null) {
            LinearLayout bar = hbox();
            bar.setPadding(dp(2), dp(4), dp(2), dp(1));
            if (b.positive != null) bar.addView(dialogButton(b, b.positive, b.onPositive), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (b.neutral != null) bar.addView(dialogButton(b, b.neutral, b.onNeutral), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (b.negative != null) bar.addView(dialogButton(b, b.negative, b.onNegative), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            parts.add(bar);
            bright.add(null);
        }
        for (int i = 0; i < parts.size(); i++) {
            View p = parts.get(i);
            Boolean br = bright.get(i);
            boolean first = i == 0, last = i == parts.size() - 1;
            int id;
            if (br == null) {
                id = R.drawable.cl_popup_bottom_medium; // button panel
            } else if (parts.size() == 1) {
                id = br ? R.drawable.cl_popup_full_bright : R.drawable.cl_popup_full_dark;
            } else if (first) {
                id = br ? R.drawable.cl_popup_top_bright : R.drawable.cl_popup_top_dark;
            } else if (last) {
                id = br ? R.drawable.cl_popup_bottom_bright : R.drawable.cl_popup_bottom_dark;
            } else {
                id = br ? R.drawable.cl_popup_center_bright : R.drawable.cl_popup_center_dark;
            }
            int pl = p.getPaddingLeft(), pt = p.getPaddingTop(), pr = p.getPaddingRight(), pb = p.getPaddingBottom();
            p.setBackgroundDrawable(res(id));
            p.setPadding(Math.max(pl, p.getPaddingLeft()), Math.max(pt, p.getPaddingTop()), Math.max(pr, p.getPaddingRight()), Math.max(pb, p.getPaddingBottom()));
            LinearLayout.LayoutParams lp = matchWrap();
            if (p instanceof ScrollView) lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            root.addView(p, lp);
            if (first && b.title != null && parts.size() > 1) {
                // Title divider of alert_dialog.xml.
                ImageView div = new ImageView(context);
                div.setBackgroundDrawable(res(R.drawable.cl_divider_horizontal_dark));
                root.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
            }
        }
        LinearLayout wrapper = vbox();
        wrapper.addView(root, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Dialog d = rawDialog(wrapper, b.cancelable, 0.6f, 480);
        b.dialog = d;
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (b.onDismiss != null) b.onDismiss.run();
            }
        });
        d.show();
        return d;
    }

    private View dialogButton(final DialogBuilder b, String label, final Runnable r) {
        return button(label, B_SECONDARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!b.keepOpen) b.dismiss();
                if (r != null) r.run();
            }
        });
    }

    /** Context menus in 2.x were list dialogs with the item as title. */
    @Override
    public void menu(View anchor, String title, final List<Action> actions) {
        List<String> labels = new ArrayList<String>();
        for (Action a : actions) labels.add(a.checkable && a.checked ? "✓ " + a.label : a.label);
        dialog().title(title).items(labels.toArray(new String[labels.size()]), new DialogBuilder.ItemListener() {
            @Override
            public void onItem(int index) {
                Action a = actions.get(index);
                if (a.enabled && a.run != null) a.run.run();
            }
        }).show();
    }

    /** transient_notification.xml on toast_frame. */
    @Override
    public void toast(String message) {
        TextView t = new TextView(context);
        t.setText(message);
        t.setTypeface(Fonts.classic(context, Fonts.REGULAR));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTextColor(0xFFFFFFFF);
        t.setShadowLayer(2.75f, 0, 0, 0xBB000000);
        t.setBackgroundDrawable(res(R.drawable.cl_toast_frame));
        Toast toast = new Toast(context);
        toast.setView(t);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
    }

}
