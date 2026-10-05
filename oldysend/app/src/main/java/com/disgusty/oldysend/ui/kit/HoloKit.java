package com.disgusty.oldysend.ui.kit;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.disgusty.oldysend.R;
import com.disgusty.oldysend.ui.icon.IconDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Holo (Android 3.0–4.4), built from the Android 4.4.4 framework resources: solid action bar with app icon,
 * stacked action bar tabs, holo blue accents, ON/OFF switch, borderless dialog buttons, Roboto.
 */
final class HoloKit extends Kit {
    private static final int HOLO_BLUE = 0xFF33B5E5;
    private final boolean dark;

    HoloKit(Activity a, Scheme s) {
        super(a, s);
        dark = s.dark;
    }

    @SuppressWarnings("deprecation")
    private Drawable res(int id) {
        return activity.getResources().getDrawable(id);
    }

    private int pick(int darkId, int lightId) {
        return dark ? darkId : lightId;
    }

    // ---------------------------------------------------------------- text

    @Override
    public Typeface typeface(int role) {
        boolean bold = role == T_SECTION;
        boolean light = role == T_DISPLAY;
        if (light && com.disgusty.oldysend.util.Sdk.atLeast(16)) return Typeface.create("sans-serif-light", Typeface.NORMAL);
        return Fonts.roboto(context, bold ? Fonts.BOLD : Fonts.REGULAR);
    }

    /** TextAppearance.Holo: Large 22, Medium 18, Small 14. */
    @Override
    public float textSize(int role) {
        switch (role) {
            case T_DISPLAY:
                return 34;
            case T_HEADLINE:
            case T_TITLE:
                return 22;
            case T_SUBTITLE:
                return 18;
            case T_BODY:
                return 16;
            default:
                return 14;
        }
    }

    @Override
    public void styleText(TextView t, int role) {
        super.styleText(t, role);
        if (role == T_SECTION && com.disgusty.oldysend.util.Sdk.atLeast(14)) Api14.caps(t);
    }

    @android.annotation.TargetApi(14)
    private static final class Api14 {
        static void caps(TextView t) {
            t.setAllCaps(true);
        }
    }

    @Override
    public int textColor(int role) {
        if (role == -1) return c.textDisabled;
        if (role == T_SECONDARY || role == T_CAPTION || role == T_SECTION) return c.onSurfaceVariant;
        return c.onSurface;
    }

    /** Holo icon guidelines: #333333 at 60% on light, white at 80% on dark. */
    @Override
    public int iconColor() {
        return dark ? 0xCCFFFFFF : 0x99333333;
    }

    @Override
    public int pagePadding() {
        return dp(16);
    }

    @Override
    public Drawable logo(int sizePx) {
        return new LogoDrawable(sizePx, 0xFF0099CC, HOLO_BLUE, 0xFFFFFFFF)
                .shader(Draw.vertical(sizePx, 0xFF5CC6EC, 0xFF1C9FD0));
    }

    // ---------------------------------------------------------------- window, action bar

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
        return new ActionBar();
    }

    private Drawable itemBackground() {
        return Draw.states(res(pick(R.drawable.ho_list_pressed_holo_dark, R.drawable.ho_list_pressed_holo_light)),
                res(R.drawable.ho_list_focused_holo), null, null, null);
    }

    private final class ActionBar extends AppBar {
        private final LinearLayout root = hbox();
        private final LinearLayout home = hbox();
        private final ImageView upIcon = new ImageView(context);
        private final ImageView appIcon = new ImageView(context);
        private final TextView titleView = new TextView(context);
        private final TextView subtitleView = new TextView(context);
        private final LinearLayout actionsBox = hbox();

        ActionBar() {
            root.setBackgroundDrawable(res(pick(R.drawable.ho_ab_solid_dark_holo, R.drawable.ho_ab_solid_light_holo)));
            root.setMinimumHeight(dp(48));
            upIcon.setImageDrawable(res(pick(R.drawable.ho_ic_ab_back_holo_dark_am, R.drawable.ho_ic_ab_back_holo_light_am)));
            home.addView(upIcon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
            appIcon.setImageDrawable(logo(dp(32)));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(32), dp(32));
            ip.leftMargin = dp(8);
            ip.rightMargin = dp(8);
            home.addView(appIcon, ip);
            LinearLayout texts = vbox();
            titleView.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            titleView.setTextColor(dark ? 0xFFFFFFFF : 0xFF000000);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            subtitleView.setTextColor(dark ? 0xFFBEBEBE : 0xFF323232);
            subtitleView.setSingleLine(true);
            texts.addView(titleView);
            texts.addView(subtitleView);
            home.addView(texts);
            home.setPadding(0, 0, dp(8), 0);
            root.addView(home, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            root.addView(actionsBox);
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
            upIcon.setVisibility(up != null ? View.VISIBLE : View.GONE);
            if (up != null) {
                home.setBackgroundDrawable(itemBackground());
                home.setClickable(true);
                home.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        up.run();
                    }
                });
            } else {
                home.setBackgroundDrawable(null);
                home.setClickable(false);
                ((LinearLayout.LayoutParams) appIcon.getLayoutParams()).leftMargin = dp(8);
            }
            actionsBox.removeAllViews();
            final List<Action> overflow = new ArrayList<Action>();
            for (final Action a : actions) {
                if (a.always && a.icon != null) {
                    View b = actionButton(icon(a.icon, iconColor(), 32), a.label, new View.OnClickListener() {
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
                Drawable more = res(pick(R.drawable.ho_ic_menu_moreoverflow_normal_holo_dark, R.drawable.ho_ic_menu_moreoverflow_normal_holo_light));
                anchor[0] = actionButton(more, null, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        menu(anchor[0], null, overflow);
                    }
                });
                actionsBox.addView(anchor[0]);
            }
        }

        private View actionButton(Drawable d, String description, View.OnClickListener l) {
            ImageView iv = new ImageView(context);
            iv.setImageDrawable(d);
            iv.setScaleType(ImageView.ScaleType.CENTER);
            iv.setBackgroundDrawable(itemBackground());
            iv.setPadding(dp(12), 0, dp(12), 0);
            iv.setMinimumWidth(dp(56));
            iv.setClickable(true);
            iv.setOnClickListener(l);
            if (description != null) com.disgusty.oldysend.ui.Compat.description(iv, description);
            iv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
            return iv;
        }

        @Override
        public void openMenu() {
            int n = actionsBox.getChildCount();
            if (n > 0) actionsBox.getChildAt(n - 1).performClick();
        }
    }

    // ---------------------------------------------------------------- stacked action bar tabs

    @Override
    public NavBar navBar(String[] labels, Ic[] icons, NavBar.Listener listener) {
        return new Tabs(labels, icons, listener);
    }

    private final class Tabs extends NavBar {
        private final LinearLayout root = hbox();
        private final List<TextView> tabs = new ArrayList<TextView>();

        Tabs(String[] labels, Ic[] icons, Listener listener) {
            super(labels, icons, listener);
            root.setBackgroundDrawable(res(pick(R.drawable.ho_ab_stacked_solid_dark_holo, R.drawable.ho_ab_stacked_solid_light_holo)));
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                if (i > 0) {
                    // actionBarDivider with 12dip divider padding
                    View div = new View(context);
                    div.setBackgroundColor(c.divider);
                    LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(24));
                    root.addView(div, dp);
                }
                TextView t = new TextView(context);
                t.setText(labels[i].toUpperCase());
                t.setTypeface(Fonts.roboto(context, Fonts.BOLD));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                t.setTextColor(dark ? 0xFFFFFFFF : 0xFF000000);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                t.setPadding(dp(16), 0, dp(16), 0);
                StateListDrawable s = new StateListDrawable();
                s.addState(new int[]{android.R.attr.state_selected, android.R.attr.state_pressed}, res(R.drawable.ho_tab_selected_pressed_holo));
                s.addState(new int[]{android.R.attr.state_pressed}, res(R.drawable.ho_tab_unselected_pressed_holo));
                s.addState(new int[]{android.R.attr.state_selected, android.R.attr.state_focused}, res(R.drawable.ho_tab_selected_focused_holo));
                s.addState(new int[]{android.R.attr.state_focused}, res(R.drawable.ho_tab_unselected_focused_holo));
                s.addState(Draw.SELECTED, res(R.drawable.ho_tab_selected_holo));
                s.addState(Draw.ANY, res(R.drawable.ho_tab_unselected_holo));
                t.setBackgroundDrawable(s);
                t.setClickable(true);
                t.setFocusable(true);
                t.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clicked(index);
                    }
                });
                root.addView(t, new LinearLayout.LayoutParams(0, dp(48), 1));
                tabs.add(t);
            }
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
            for (int i = 0; i < tabs.size(); i++) tabs.get(i).setSelected(i == selected);
        }
    }

    // ---------------------------------------------------------------- lists

    @Override
    public Row row() {
        Row.Metrics m = new Row.Metrics();
        m.minHeightOneLine = dp(48);
        m.minHeightTwoLine = dp(64);
        m.paddingStart = dp(16);
        m.paddingEnd = dp(16);
        m.paddingVertical = dp(8);
        m.iconSize = dp(32);
        m.iconGap = dp(16);
        return new Row(this, m);
    }

    @Override
    public Drawable listSelector() {
        return Draw.states(res(pick(R.drawable.ho_list_pressed_holo_dark, R.drawable.ho_list_pressed_holo_light)),
                res(pick(R.drawable.ho_list_selector_focused_holo_dark, R.drawable.ho_list_selector_focused_holo_light)), null, null, null);
    }

    @Override
    public void styleList(ListView list) {
        list.setDivider(res(pick(R.drawable.ho_list_divider_holo_dark, R.drawable.ho_list_divider_holo_light)));
        list.setSelector(listSelector());
        list.setCacheColorHint(0);
        list.setBackgroundColor(c.background);
    }

    @Override
    public View divider() {
        ImageView v = new ImageView(context);
        v.setBackgroundDrawable(res(pick(R.drawable.ho_list_divider_holo_dark, R.drawable.ho_list_divider_holo_light)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        p.leftMargin = dp(8);
        p.rightMargin = dp(8);
        v.setLayoutParams(p);
        return v;
    }

    /** Widget.Holo.TextView.ListSeparator: caps, bold 14sp, list_section_divider_holo underline. */
    @Override
    public View sectionHeader(String title) {
        TextView t = text(T_SECTION, title);
        t.setText(title.toUpperCase());
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setBackgroundDrawable(res(pick(R.drawable.ho_list_section_divider_holo_dark, R.drawable.ho_list_section_divider_holo_light)));
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        t.setMinHeight(dp(25));
        t.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout wrap = vbox();
        wrap.setPadding(dp(8), dp(16), dp(8), 0);
        wrap.addView(t, matchWrap());
        return wrap;
    }

    // ---------------------------------------------------------------- controls

    private Drawable btnDefault() {
        return Draw.states(res(pick(R.drawable.ho_btn_default_pressed_holo_dark, R.drawable.ho_btn_default_pressed_holo_light)),
                res(pick(R.drawable.ho_btn_default_focused_holo_dark, R.drawable.ho_btn_default_focused_holo_light)), null,
                res(pick(R.drawable.ho_btn_default_disabled_holo_dark, R.drawable.ho_btn_default_disabled_holo_light)),
                res(pick(R.drawable.ho_btn_default_normal_holo_dark, R.drawable.ho_btn_default_normal_holo_light)));
    }

    /** Widget.Holo.Button (btn_default_holo, 18sp) and Widget.Holo.Button.Borderless.Small (14sp). */
    @Override
    public View button(String label, int kind, View.OnClickListener onClick) {
        TextView b = new TextView(context);
        b.setText(label);
        b.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setMinHeight(dp(48));
        b.setMinWidth(dp(64));
        if (kind == B_TEXT) {
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            b.setTextColor(c.onSurface);
            b.setBackgroundDrawable(listSelector());
            b.setPadding(dp(8), 0, dp(8), 0);
        } else {
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            b.setTextColor(kind == B_PRIMARY ? (dark ? HOLO_BLUE : 0xFF0099CC) : c.onSurface);
            b.setBackgroundDrawable(btnDefault());
            b.setPadding(dp(16), 0, dp(16), 0);
        }
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        return b;
    }

    @Override
    public View iconButton(Ic icon, String description, View.OnClickListener onClick) {
        ImageView iv = new ImageView(context);
        iv.setImageDrawable(icon(icon, iconColor(), 32));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setBackgroundDrawable(listSelector());
        iv.setClickable(true);
        iv.setFocusable(true);
        iv.setOnClickListener(onClick);
        if (description != null) com.disgusty.oldysend.ui.Compat.description(iv, description);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return iv;
    }

    @Override
    public View tile(Ic icon, String label, View.OnClickListener onClick) {
        TextView t = new TextView(context);
        t.setText(label);
        t.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(c.onSurface);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        IconDrawable d = icon(icon, iconColor(), 32);
        d.setBounds(0, 0, dp(32), dp(32));
        t.setCompoundDrawables(null, d, null, null);
        t.setCompoundDrawablePadding(dp(4));
        t.setBackgroundDrawable(btnDefault());
        t.setMinHeight(dp(80));
        t.setClickable(true);
        t.setOnClickListener(onClick);
        return t;
    }

    @Override
    public Toggle toggle(int kind) {
        if (kind == SWITCH) return new Toggle(context, new HoloSwitch());
        StateListDrawable s = new StateListDrawable();
        if (kind == RADIO) {
            s.addState(new int[]{android.R.attr.state_checked, android.R.attr.state_pressed}, res(pick(R.drawable.ho_btn_radio_on_pressed_holo_dark, R.drawable.ho_btn_radio_on_pressed_holo_light)));
            s.addState(new int[]{android.R.attr.state_checked}, res(pick(R.drawable.ho_btn_radio_on_holo_dark, R.drawable.ho_btn_radio_on_holo_light)));
            s.addState(new int[]{android.R.attr.state_pressed}, res(pick(R.drawable.ho_btn_radio_off_pressed_holo_dark, R.drawable.ho_btn_radio_off_pressed_holo_light)));
            s.addState(Draw.ANY, res(pick(R.drawable.ho_btn_radio_off_holo_dark, R.drawable.ho_btn_radio_off_holo_light)));
        } else {
            s.addState(new int[]{android.R.attr.state_checked, android.R.attr.state_pressed}, res(pick(R.drawable.ho_btn_check_on_pressed_holo_dark, R.drawable.ho_btn_check_on_pressed_holo_light)));
            s.addState(new int[]{android.R.attr.state_checked, -android.R.attr.state_enabled}, res(pick(R.drawable.ho_btn_check_on_disabled_holo_dark, R.drawable.ho_btn_check_on_disabled_holo_light)));
            s.addState(new int[]{android.R.attr.state_checked}, res(pick(R.drawable.ho_btn_check_on_holo_dark, R.drawable.ho_btn_check_on_holo_light)));
            s.addState(new int[]{android.R.attr.state_pressed}, res(pick(R.drawable.ho_btn_check_off_pressed_holo_dark, R.drawable.ho_btn_check_off_pressed_holo_light)));
            s.addState(new int[]{-android.R.attr.state_enabled}, res(pick(R.drawable.ho_btn_check_off_disabled_holo_dark, R.drawable.ho_btn_check_off_disabled_holo_light)));
            s.addState(Draw.ANY, res(pick(R.drawable.ho_btn_check_off_holo_dark, R.drawable.ho_btn_check_off_holo_light)));
        }
        return new Toggle(context, s);
    }

    /** Widget.Holo.CompoundButton.Switch: switch_bg track, switch_thumb with ON/OFF (14sp bold), min width 96dip. */
    private final class HoloSwitch extends Drawable {
        private final Drawable track = res(pick(R.drawable.ho_switch_bg_holo_dark, R.drawable.ho_switch_bg_holo_light));
        private final Drawable thumbOn = res(pick(R.drawable.ho_switch_thumb_activated_holo_dark, R.drawable.ho_switch_thumb_activated_holo_light));
        private final Drawable thumbOff = res(pick(R.drawable.ho_switch_thumb_holo_dark, R.drawable.ho_switch_thumb_holo_light));
        private final Drawable thumbPressed = res(pick(R.drawable.ho_switch_thumb_pressed_holo_dark, R.drawable.ho_switch_thumb_pressed_holo_light));
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean checked;
        private boolean pressed;
        private float pos;
        private long animStart;
        private float animFrom;
        private boolean first = true;

        HoloSwitch() {
            text.setTypeface(Fonts.roboto(context, Fonts.BOLD));
            text.setTextSize(14 * activity.getResources().getDisplayMetrics().scaledDensity);
            text.setColor(0xFFFFFFFF);
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(96);
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.max(dp(40), thumbOff.getIntrinsicHeight());
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean c = MaterialDrawables.has(state, android.R.attr.state_checked);
            boolean p = MaterialDrawables.has(state, android.R.attr.state_pressed);
            if (c != checked) {
                if (first || !animations) pos = c ? 1 : 0;
                else {
                    animFrom = pos;
                    animStart = SystemClock.uptimeMillis();
                }
            }
            first = false;
            boolean changed = c != checked || p != pressed;
            checked = c;
            pressed = p;
            invalidateSelf();
            return changed;
        }

        @Override
        public void draw(Canvas canvas) {
            if (animStart > 0) {
                float t = Math.min(1f, (SystemClock.uptimeMillis() - animStart) / 150f);
                pos = animFrom + ((checked ? 1 : 0) - animFrom) * t;
                if (t >= 1) animStart = 0;
                else invalidateSelf();
            }
            Rect b = getBounds();
            track.setBounds(b);
            track.draw(canvas);
            // Switch.onMeasure: thumb = widest label + 2 * thumbTextPadding (12dip) + thumb padding.
            Rect tp = new Rect();
            thumbOff.getPadding(tp);
            int thumbW = Math.max(thumbOff.getIntrinsicWidth(), Math.round(text.measureText("OFF")) + dp(24) + tp.left + tp.right);
            // The thumb 9-patch has transparent margins; the visible thumb must cover about half the track.
            thumbW = Math.max(thumbW, Math.round(b.width() * 0.62f));
            int x = b.left + Math.round((b.width() - thumbW) * pos);
            Drawable thumb = pressed ? thumbPressed : (checked ? thumbOn : thumbOff);
            thumb.setBounds(x, b.top, x + thumbW, b.bottom);
            thumb.draw(canvas);
            Paint.FontMetrics fm = text.getFontMetrics();
            float ty = b.exactCenterY() - (fm.ascent + fm.descent) / 2;
            text.setColor(checked ? 0xFFFFFFFF : (dark ? 0xFFBEBEBE : 0xFF505050));
            canvas.drawText(pos > 0.5f ? "ON" : "OFF", x + thumbW / 2f, ty, text);
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

    @Override
    public Field field(String label, boolean multiline) {
        LinearLayout box = vbox();
        if (label != null && label.length() > 0) {
            TextView l = text(T_SECONDARY, label);
            l.setPadding(dp(4), 0, 0, 0);
            box.addView(l);
        }
        EditText e = new EditText(context);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, res(pick(R.drawable.ho_textfield_disabled_holo_dark, R.drawable.ho_textfield_disabled_holo_light)));
        s.addState(new int[]{android.R.attr.state_focused}, res(pick(R.drawable.ho_textfield_activated_holo_dark, R.drawable.ho_textfield_activated_holo_light)));
        s.addState(Draw.ANY, res(pick(R.drawable.ho_textfield_default_holo_dark, R.drawable.ho_textfield_default_holo_light)));
        e.setBackgroundDrawable(s);
        e.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        e.setTextColor(c.onSurface);
        e.setHintTextColor(c.textDisabled);
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

    /** progress_horizontal_holo (bg + clipped primary) and the 8 indeterminate frames. */
    @Override
    public Progress progress(boolean indeterminate) {
        final ClipDrawable clip = new ClipDrawable(res(pick(R.drawable.ho_progress_primary_holo_dark, R.drawable.ho_progress_primary_holo_light)),
                Gravity.LEFT, ClipDrawable.HORIZONTAL);
        final LayerDrawable layers = new LayerDrawable(new Drawable[]{res(pick(R.drawable.ho_progress_bg_holo_dark, R.drawable.ho_progress_bg_holo_light)), clip});
        final int[] frames = {R.drawable.ho_progressbar_indeterminate_holo1, R.drawable.ho_progressbar_indeterminate_holo2,
                R.drawable.ho_progressbar_indeterminate_holo3, R.drawable.ho_progressbar_indeterminate_holo4, R.drawable.ho_progressbar_indeterminate_holo5,
                R.drawable.ho_progressbar_indeterminate_holo6, R.drawable.ho_progressbar_indeterminate_holo7, R.drawable.ho_progressbar_indeterminate_holo8};
        final AnimationDrawable anim = new AnimationDrawable();
        for (int f : frames) anim.addFrame(res(f), 50);
        final boolean[] ind = {indeterminate};
        final View v = new View(context) {
            private final long start = SystemClock.uptimeMillis();

            @Override
            protected void onDraw(Canvas canvas) {
                if (!ind[0]) {
                    layers.setBounds(0, 0, getWidth(), getHeight());
                    layers.draw(canvas);
                    return;
                }
                Drawable d = anim.getFrame((int) (((SystemClock.uptimeMillis() - start) / 50) % 8));
                int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : getWidth();
                for (int x = 0; x < getWidth(); x += w) {
                    d.setBounds(x, 0, x + w, getHeight());
                    d.draw(canvas);
                }
                postInvalidateDelayed(50);
            }
        };
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
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
            public void setIndeterminate(boolean indeterminateValue) {
                ind[0] = indeterminateValue;
                v.invalidate();
            }
        };
    }

    /** progress_medium_holo: outer ring rotating clockwise, inner ring counter-clockwise. */
    @Override
    public View spinner() {
        final Drawable outer = res(R.drawable.ho_spinner_48_outer_holo);
        final Drawable inner = res(R.drawable.ho_spinner_48_inner_holo);
        final int size = dp(48);
        return new View(context) {
            private final long start = SystemClock.uptimeMillis();

            @Override
            protected void onMeasure(int w, int h) {
                setMeasuredDimension(size, size);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                float t = (SystemClock.uptimeMillis() - start) / 3500f;
                canvas.save();
                canvas.rotate(t * 1080 % 360, size / 2f, size / 2f);
                outer.setBounds(0, 0, size, size);
                outer.draw(canvas);
                canvas.restore();
                canvas.save();
                canvas.rotate(-(t * 720 % 360), size / 2f, size / 2f);
                inner.setBounds(0, 0, size, size);
                inner.draw(canvas);
                canvas.restore();
                postInvalidateDelayed(16);
            }
        };
    }

    /** Holo had no segmented control: a row of btn_default_holo buttons, the chosen one shown pressed (blue). */
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
                    t.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
                    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    t.setGravity(Gravity.CENTER);
                    t.setSingleLine(true);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setMinHeight(dp(48));
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
                    TextView t = items.get(i);
                    boolean sel = i == selected;
                    t.setBackgroundDrawable(sel ? res(pick(R.drawable.ho_btn_default_pressed_holo_dark, R.drawable.ho_btn_default_pressed_holo_light)) : btnDefault());
                    t.setTextColor(sel ? (dark ? 0xFFFFFFFF : 0xFF000000) : c.onSurfaceVariant);
                }
            }
        };
    }

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
        t.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(dark ? 0xFFBEBEBE : 0xFF505050);
        t.setSingleLine(true);
        t.setBackgroundDrawable(Draw.stroke(0, c.divider, Math.max(1, dp(1)), dp(2)));
        t.setPadding(dp(6), dp(1), dp(6), dp(1));
        return t;
    }

    // ---------------------------------------------------------------- dialogs (alert_dialog_holo)

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
        root.setBackgroundDrawable(res(pick(R.drawable.ho_dialog_full_holo_dark, R.drawable.ho_dialog_full_holo_light)));
        if (b.title != null) {
            TextView t = new TextView(context);
            t.setText(b.title);
            t.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            t.setTextColor(HOLO_BLUE);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(16), 0, dp(16), 0);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setMinHeight(dp(64));
            root.addView(t, matchWrap());
            View div = new View(context);
            div.setBackgroundColor(HOLO_BLUE);
            root.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        }
        LinearLayout content = vbox();
        if (b.message != null) {
            TextView m = text(T_BODY, b.message);
            m.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            m.setPadding(dp(16), dp(8), dp(16), dp(8));
            content.addView(m);
        }
        if (b.items != null) {
            for (int i = 0; i < b.items.length; i++) {
                final int index = i;
                LinearLayout item = hbox();
                item.setMinimumHeight(dp(48));
                item.setPadding(dp(16), 0, dp(12), 0);
                TextView t = text(T_SUBTITLE, b.items[i]);
                item.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                Toggle tg = null;
                if (b.itemsMode == DialogBuilder.ITEMS_SINGLE) {
                    tg = toggle(RADIO).passive();
                    tg.setChecked(i == b.checkedItem);
                    item.addView(tg);
                } else if (b.itemsMode == DialogBuilder.ITEMS_MULTI) {
                    tg = toggle(CHECKBOX).passive();
                    tg.setChecked(b.checkedItems[i]);
                    item.addView(tg);
                }
                item.setBackgroundDrawable(listSelector());
                item.setClickable(true);
                final Toggle toggle = tg;
                final LinearLayout contentRef = content;
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
                            int row = 0;
                            for (int j = 0; j < contentRef.getChildCount(); j++) {
                                View child = contentRef.getChildAt(j);
                                if (child instanceof LinearLayout && ((LinearLayout) child).getChildCount() > 1
                                        && ((LinearLayout) child).getChildAt(1) instanceof Toggle) {
                                    ((Toggle) ((LinearLayout) child).getChildAt(1)).setChecked(row == index);
                                    row++;
                                }
                            }
                            if (b.positive != null) return;
                        }
                        if (b.itemListener != null) b.itemListener.onItem(index);
                        b.dismiss();
                    }
                });
                content.addView(item, matchWrap());
                if (i < b.items.length - 1) content.addView(divider());
            }
        }
        if (b.view != null) {
            LinearLayout.LayoutParams p = matchWrap();
            p.setMargins(dp(16), dp(8), dp(16), dp(8));
            content.addView(b.view, p);
        }
        ScrollView sv = new ScrollView(context);
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (b.positive != null || b.negative != null || b.neutral != null) {
            // Holo.ButtonBar.AlertDialog: top divider, borderless buttons separated by vertical dividers.
            View top = new View(context);
            top.setBackgroundColor(c.divider);
            root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
            LinearLayout bar = hbox();
            List<View> buttons = new ArrayList<View>();
            if (b.negative != null) buttons.add(dialogButton(b, b.negative, b.onNegative));
            if (b.neutral != null) buttons.add(dialogButton(b, b.neutral, b.onNeutral));
            if (b.positive != null) buttons.add(dialogButton(b, b.positive, b.onPositive));
            for (int i = 0; i < buttons.size(); i++) {
                if (i > 0) {
                    View div = new View(context);
                    div.setBackgroundColor(c.divider);
                    bar.addView(div, new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(32)));
                }
                bar.addView(buttons.get(i), new LinearLayout.LayoutParams(0, dp(48), 1));
            }
            root.addView(bar, matchWrap());
        }
        Dialog d = rawDialog(root, b.cancelable, 0.6f, 440);
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
        TextView t = (TextView) button(label, B_TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!b.keepOpen) b.dismiss();
                if (r != null) r.run();
            }
        });
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return t;
    }

    /** Overflow / popup menu: menu_dropdown_panel_holo with 48dp, 18sp items. */
    @Override
    public void menu(View anchor, String title, List<Action> actions) {
        LinearLayout list = vbox();
        final PopupWindow[] holder = new PopupWindow[1];
        for (final Action a : actions) {
            LinearLayout item = hbox();
            item.setMinimumHeight(dp(48));
            item.setPadding(dp(16), 0, dp(16), 0);
            TextView t = new TextView(context);
            t.setText(a.label);
            t.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            t.setTextColor(a.enabled ? c.onSurface : c.textDisabled);
            t.setSingleLine(true);
            item.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (a.checkable) {
                Toggle tg = toggle(CHECKBOX).passive();
                tg.setChecked(a.checked);
                item.addView(tg);
            }
            item.setBackgroundDrawable(listSelector());
            item.setClickable(a.enabled);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (holder[0] != null) holder[0].dismiss();
                    if (a.run != null) a.run.run();
                }
            });
            list.addView(item, matchWrap());
        }
        ScrollView sv = new ScrollView(context);
        sv.setBackgroundDrawable(res(pick(R.drawable.ho_menu_dropdown_panel_holo_dark, R.drawable.ho_menu_dropdown_panel_holo_light)));
        sv.addView(list);
        sv.measure(View.MeasureSpec.makeMeasureSpec(dp(320), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
        int width = Math.max(dp(192), sv.getMeasuredWidth());
        PopupWindow pw = new PopupWindow(sv, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        com.disgusty.oldysend.ui.Compat.outsideTouchable(pw);
        holder[0] = pw;
        pw.showAsDropDown(anchor, anchor.getWidth() - width, 0);
    }

    @Override
    public void toast(String message) {
        TextView t = new TextView(context);
        t.setText(message);
        t.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(0xFFFFFFFF);
        t.setShadowLayer(2.75f, 0, 0, 0xBB000000);
        t.setBackgroundDrawable(res(R.drawable.ho_toast_frame));
        Toast toast = new Toast(context);
        toast.setView(t);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    public IconDrawable icon(Ic ic, int color, int sizeDp) {
        return super.icon(ic, color, sizeDp);
    }
}
