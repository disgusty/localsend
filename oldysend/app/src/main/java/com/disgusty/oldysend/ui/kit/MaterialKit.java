package com.disgusty.oldysend.ui.kit;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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

import com.disgusty.oldysend.ui.icon.IconDrawable;
import com.disgusty.oldysend.util.Sdk;

import java.util.List;

/**
 * Shared implementation of the two Material styles. Md1Kit (Material Design 2014, Android 5–11) and
 * Md3Kit (Material 3, Android 12+) supply the spec values that differ: shapes, colors, type scale, layout.
 */
abstract class MaterialKit extends Kit {
    protected final boolean m3;

    MaterialKit(Activity a, Scheme s, boolean m3) {
        super(a, s);
        this.m3 = m3;
    }

    // ---- spec values ----
    protected abstract float buttonRadius();

    protected abstract float cardRadius();

    protected abstract float dialogRadius();

    protected abstract float menuRadius();

    protected abstract int dialogBackground();

    protected abstract int menuBackground();

    protected abstract boolean capsButtons();

    /** Ripple color on surfaces (M1: 12%/20% black/white; M3: onSurface 10%). */
    protected int rippleOn(int contentColor) {
        return Scheme.withAlpha(contentColor, m3 ? 0.12f : 0.20f);
    }

    // ---------------------------------------------------------------- text

    @Override
    public Typeface typeface(int role) {
        boolean medium = role == T_LABEL || role == T_SECTION || (!m3 && (role == T_TITLE || role == T_HEADLINE));
        return Fonts.roboto(context, medium ? Fonts.MEDIUM : Fonts.REGULAR);
    }

    @Override
    public int textColor(int role) {
        switch (role) {
            case -1:
                return c.textDisabled;
            case T_SECONDARY:
            case T_CAPTION:
                return c.onSurfaceVariant;
            case T_SECTION:
                return m3 ? c.primary : c.accent;
            default:
                return c.onSurface;
        }
    }

    @Override
    public int iconColor() {
        return c.onSurfaceVariant;
    }

    @Override
    public int pagePadding() {
        return dp(16);
    }

    // ---------------------------------------------------------------- touch feedback

    /** Bounded ripple over an optional background shape. */
    protected Drawable touch(int contentColor, Drawable background, float radius) {
        Drawable mask = Draw.rect(0xFFFFFFFF, radius);
        return Draw.ripple(rippleOn(contentColor), background, mask, false);
    }

    /** Pre-5.0 ink drawables need the touch point. */
    protected void trackHotspot(View v) {
        if (Sdk.atLeast(21)) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                Drawable bg = view.getBackground();
                Draw.InkDrawable ink = null;
                if (bg instanceof Draw.InkDrawable) ink = (Draw.InkDrawable) bg;
                else if (bg instanceof android.graphics.drawable.LayerDrawable) {
                    android.graphics.drawable.LayerDrawable l = (android.graphics.drawable.LayerDrawable) bg;
                    Drawable top = l.getDrawable(l.getNumberOfLayers() - 1);
                    if (top instanceof Draw.InkDrawable) ink = (Draw.InkDrawable) top;
                }
                if (ink != null && e.getAction() == MotionEvent.ACTION_DOWN) ink.setHotspot(e.getX(), e.getY());
                return false;
            }
        });
    }

    @Override
    public Drawable listSelector() {
        return touch(c.onSurface, null, 0);
    }

    @Override
    public void styleList(ListView list) {
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setCacheColorHint(0);
        list.setBackgroundColor(c.background);
    }

    @Override
    public View divider() {
        View v = new View(context);
        v.setBackgroundColor(m3 ? c.outlineVariant : c.divider);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return v;
    }

    @Override
    public View sectionHeader(String title) {
        TextView t = text(T_SECTION, title);
        t.setPadding(dp(16), dp(m3 ? 16 : 16), dp(16), dp(m3 ? 8 : 8));
        t.setMinHeight(dp(m3 ? 0 : 48));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    // ---------------------------------------------------------------- buttons

    @Override
    public View button(String label, int kind, View.OnClickListener onClick) {
        TextView b = new TextView(context);
        b.setText(capsButtons() ? label.toUpperCase() : label);
        b.setTypeface(Fonts.roboto(context, Fonts.MEDIUM));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setMinHeight(dp(m3 ? 40 : 36));
        b.setMinWidth(dp(m3 ? 48 : 64));
        float r = buttonRadius();
        int fg;
        Drawable bg;
        switch (kind) {
            case B_PRIMARY:
                int fill = m3 ? c.primary : c.accent;
                fg = m3 ? c.onPrimary : contrastOn(fill);
                bg = touch(fg, Draw.rect(fill, r), r);
                b.setPadding(dp(m3 ? 24 : 16), 0, dp(m3 ? 24 : 16), 0);
                break;
            case B_SECONDARY:
                if (m3) {
                    fg = c.onSecondaryContainer;
                    bg = touch(fg, Draw.rect(c.secondaryContainer, r), r);
                } else {
                    // Widget.Material.Button: colorButtonNormal
                    int normal = c.dark ? 0xFF5A595B : 0xFFD6D7D7;
                    fg = c.onSurface;
                    bg = touch(fg, Draw.rect(normal, r), r);
                }
                b.setPadding(dp(m3 ? 24 : 16), 0, dp(m3 ? 24 : 16), 0);
                break;
            default:
                fg = m3 ? c.primary : c.accent;
                bg = touch(fg, null, r);
                b.setPadding(dp(m3 ? 12 : 8), 0, dp(m3 ? 12 : 8), 0);
                b.setMinWidth(dp(m3 ? 48 : 64));
                break;
        }
        b.setTextColor(fg);
        b.setBackgroundDrawable(bg);
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        trackHotspot(b);
        if (!m3 && kind != B_TEXT) Draw.elevate(b, dp(2), r);
        return b;
    }

    protected static int contrastOn(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF;
        double lum = 0.299 * r + 0.587 * g + 0.114 * bl;
        return lum > 160 ? 0xDE000000 : 0xFFFFFFFF;
    }

    @Override
    public View iconButton(Ic icon, String description, View.OnClickListener onClick) {
        return iconButton(icon, iconColor(), description, onClick);
    }

    protected View iconButton(Ic icon, int color, String description, View.OnClickListener onClick) {
        ImageView iv = new ImageView(context);
        iv.setImageDrawable(icon(icon, color, 24));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        int size = dp(m3 ? 40 : 48);
        iv.setMinimumWidth(size);
        iv.setMinimumHeight(size);
        iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        Drawable ripple = Draw.ripple(Scheme.withAlpha(color, m3 ? 0.12f : 0.2f), null, Sdk.atLeast(21) ? Draw.oval(0xFFFFFFFF) : null, true);
        iv.setBackgroundDrawable(ripple);
        iv.setClickable(true);
        iv.setFocusable(true);
        iv.setOnClickListener(onClick);
        if (description != null) com.disgusty.oldysend.ui.Compat.description(iv, description);
        return iv;
    }

    @Override
    public View tile(Ic icon, String label, View.OnClickListener onClick) {
        LinearLayout box = vbox();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(10), dp(8), dp(10));
        int fg = m3 ? c.onSecondaryContainer : c.onSurface;
        ImageView iv = image(icon(icon, m3 ? c.onSecondaryContainer : c.accent, m3 ? 24 : 28));
        box.addView(iv);
        TextView t = text(T_LABEL, label);
        t.setTextColor(fg);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, m3 ? 12 : 13);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(0, dp(4), 0, 0);
        box.addView(t);
        float r = m3 ? dp(12) : dp(2);
        int bg = m3 ? c.secondaryContainer : c.surface;
        box.setBackgroundDrawable(touch(fg, Draw.rect(bg, r), r));
        box.setClickable(true);
        box.setOnClickListener(onClick);
        box.setMinimumHeight(dp(m3 ? 72 : 80));
        trackHotspot(box);
        if (!m3) Draw.elevate(box, dp(2), r);
        return box;
    }

    // ---------------------------------------------------------------- toggles, fields, progress

    @Override
    public Toggle toggle(int kind) {
        int on = m3 ? c.primary : c.accent;
        int off = c.onSurfaceVariant;
        Drawable d;
        if (kind == SWITCH) {
            d = m3 ? new MaterialDrawables.M3Switch(c, density, animations)
                    : new MaterialDrawables.M1Switch(c.accent, c.dark, density, animations);
        } else if (kind == CHECKBOX) {
            d = new MaterialDrawables.Check(on, off, m3 ? c.onPrimary : (c.dark ? 0xFF303030 : 0xFFFFFFFF), 2, density, animations, c.onSurface);
        } else {
            d = new MaterialDrawables.Radio(on, off, density, animations, c.onSurface);
        }
        return new Toggle(context, d);
    }

    @Override
    public Field field(String label, boolean multiline) {
        final LinearLayout box = vbox();
        final TextView labelView = text(T_CAPTION, label == null ? "" : label);
        final EditText e = new EditText(context);
        e.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTextColor(c.onSurface);
        e.setHintTextColor(c.onSurfaceVariant);
        com.disgusty.oldysend.ui.Compat.inputType(e, multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                : InputType.TYPE_CLASS_TEXT);
        if (multiline) {
            e.setMinLines(3);
            e.setMaxLines(8);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        final int focusColor = m3 ? c.primary : c.accent;
        final int idle = m3 ? c.onSurfaceVariant : (c.dark ? 0xB3FFFFFF : 0x61000000);
        final View line = new View(context);
        final TextView error = text(T_CAPTION, "");
        error.setTextColor(c.error);
        error.setVisibility(View.GONE);
        if (m3) {
            // Filled text field: surfaceContainerHighest, 4dp top corners, label inside.
            GradientDrawable bg = Draw.rect(c.surfaceContainerHighest, 0);
            bg.setCornerRadii(new float[]{dp(4), dp(4), dp(4), dp(4), 0, 0, 0, 0});
            LinearLayout filled = vbox();
            filled.setBackgroundDrawable(bg);
            filled.setPadding(dp(16), dp(8), dp(16), 0);
            filled.addView(labelView);
            e.setBackgroundDrawable(null);
            e.setPadding(0, dp(2), 0, dp(8));
            filled.addView(e, matchWrap());
            box.addView(filled, matchWrap());
        } else {
            labelView.setPadding(0, 0, 0, 0);
            box.addView(labelView);
            e.setBackgroundDrawable(null);
            e.setPadding(0, dp(8), 0, dp(8));
            box.addView(e, matchWrap());
        }
        if (label == null || label.length() == 0) labelView.setVisibility(View.GONE);
        line.setBackgroundColor(idle);
        box.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        error.setPadding(m3 ? dp(16) : 0, dp(4), 0, 0);
        box.addView(error);
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                line.setBackgroundColor(hasFocus ? focusColor : idle);
                line.getLayoutParams().height = dp(hasFocus ? 2 : 1);
                line.requestLayout();
                labelView.setTextColor(hasFocus ? focusColor : c.onSurfaceVariant);
            }
        });
        return new Field(box, e, error);
    }

    @Override
    public Progress progress(boolean indeterminate) {
        final MaterialDrawables.Linear d = m3
                ? new MaterialDrawables.Linear(c.secondaryContainer, c.primary, dp(4), true, dp(4))
                : new MaterialDrawables.Linear(Scheme.withAlpha(c.accent, 0.3f), c.accent, dp(4), false, 0);
        d.indeterminate = indeterminate;
        final DrawableView v = new DrawableView(context, d, 0, dp(4));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
        return new Progress() {
            @Override
            public View view() {
                return v;
            }

            @Override
            public void setProgress(float value) {
                d.progress = value;
                v.invalidate();
            }

            @Override
            public void setIndeterminate(boolean ind) {
                d.indeterminate = ind;
                v.invalidate();
            }
        };
    }

    @Override
    public View spinner() {
        int size = dp(48);
        MaterialDrawables.Spinner s = new MaterialDrawables.Spinner(m3 ? c.primary : c.accent, 0, size, dp(4), m3);
        return new DrawableView(context, s, size, size);
    }

    @Override
    public View chip(String label) {
        TextView t = text(T_LABEL, label);
        t.setSingleLine(true);
        if (m3) {
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            t.setTextColor(c.onSecondaryContainer);
            t.setBackgroundDrawable(Draw.rect(c.secondaryContainer, dp(8)));
            t.setPadding(dp(8), dp(3), dp(8), dp(3));
        } else {
            // Material 2014 chip: 32dp, 16dp radius, #E0E0E0 (light).
            t.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setTextColor(c.dark ? 0xFFFFFFFF : 0xDE000000);
            t.setBackgroundDrawable(Draw.rect(c.dark ? 0xFF616161 : 0xFFE0E0E0, dp(12)));
            t.setPadding(dp(10), dp(3), dp(10), dp(3));
        }
        return t;
    }

    @Override
    public ViewGroup card(boolean clickable) {
        LinearLayout card = vbox();
        float r = cardRadius();
        int bg = m3 ? c.surfaceContainerLow : c.surface;
        if (Sdk.atLeast(21)) {
            card.setBackgroundDrawable(clickable ? touch(c.onSurface, Draw.rect(bg, r), r) : Draw.rect(bg, r));
            Draw.elevate(card, dp(m3 ? 1 : 2), r);
        } else {
            Draw.ShadowDrawable shadow = new Draw.ShadowDrawable(bg, r, dp(m3 ? 1 : 2));
            card.setBackgroundDrawable(clickable ? new android.graphics.drawable.LayerDrawable(new Drawable[]{shadow, touch(c.onSurface, null, r)}) : shadow);
        }
        if (clickable) {
            card.setClickable(true);
            trackHotspot(card);
        }
        return card;
    }

    // ---------------------------------------------------------------- overlays

    @Override
    public void toast(String message) {
        TextView t = text(T_BODY, message);
        t.setTextColor(m3 ? c.inverseOnSurface : 0xFFFFFFFF);
        t.setBackgroundDrawable(Draw.rect(m3 ? c.inverseSurface : 0xFF323232, dp(m3 ? 4 : 2)));
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setMaxLines(3);
        Toast toast = new Toast(context);
        toast.setView(t);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, dp(m3 ? 96 : 24));
        toast.show();
    }

    @Override
    public void menu(View anchor, String title, final List<Action> actions) {
        LinearLayout list = vbox();
        list.setPadding(0, dp(8), 0, dp(8));
        final PopupWindow[] holder = new PopupWindow[1];
        for (final Action a : actions) {
            LinearLayout item = hbox();
            item.setMinimumHeight(dp(48));
            item.setPadding(dp(m3 ? 12 : 16), 0, dp(m3 ? 12 : 16), 0);
            if (m3 && a.icon != null) {
                ImageView iv = image(icon(a.icon, c.onSurfaceVariant, 24));
                item.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
                item.addView(space(12));
            }
            TextView t = text(m3 ? T_LABEL : T_SUBTITLE, a.label);
            if (!a.enabled) t.setTextColor(c.textDisabled);
            item.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (a.checkable) {
                Toggle tg = toggle(CHECKBOX).passive();
                tg.setChecked(a.checked);
                item.addView(tg);
            }
            if (a.enabled) {
                item.setBackgroundDrawable(touch(c.onSurface, null, 0));
                item.setClickable(true);
                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (holder[0] != null) holder[0].dismiss();
                        if (a.run != null) a.run.run();
                    }
                });
                trackHotspot(item);
            }
            list.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        FrameLayout frame = new FrameLayout(context);
        float r = menuRadius();
        if (Sdk.atLeast(21)) {
            frame.setBackgroundDrawable(Draw.rect(menuBackground(), r));
        } else {
            frame.setBackgroundDrawable(new Draw.ShadowDrawable(menuBackground(), r, dp(6)));
        }
        ScrollView sv = new ScrollView(context);
        sv.addView(list);
        frame.addView(sv);
        list.setMinimumWidth(dp(m3 ? 112 : 160));
        frame.measure(View.MeasureSpec.makeMeasureSpec(dp(280), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
        int width = Math.max(dp(m3 ? 112 : 160), frame.getMeasuredWidth());
        PopupWindow pw = new PopupWindow(frame, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        com.disgusty.oldysend.ui.Compat.outsideTouchable(pw);
        if (Sdk.atLeast(21)) PopupElevation.apply(pw, dp(m3 ? 3 : 8));
        holder[0] = pw;
        // Material menus open over the anchor, aligned to its end.
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int x = Math.min(loc[0] + anchor.getWidth() - width, screenW - width - dp(8));
        pw.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT, Math.max(dp(8), x), loc[1] + (m3 ? anchor.getHeight() : dp(4)));
    }

    @android.annotation.TargetApi(21)
    private static final class PopupElevation {
        static void apply(PopupWindow pw, float elevation) {
            pw.setElevation(elevation);
        }
    }

    @Override
    public DialogBuilder dialog() {
        return new DialogBuilder() {
            @Override
            public Dialog show() {
                return showDialog(this);
            }
        };
    }

    protected Dialog showDialog(final DialogBuilder b) {
        final LinearLayout root = vbox();
        float r = dialogRadius();
        root.setBackgroundDrawable(Draw.rect(dialogBackground(), r));
        final Dialog[] holder = new Dialog[1];
        int pad = dp(24);
        if (m3 && b.icon != null) {
            ImageView iv = image(icon(b.icon, c.secondary, 24));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
            p.topMargin = pad;
            root.addView(iv, p);
        }
        if (b.title != null) {
            TextView t = new TextView(context);
            t.setText(b.title);
            t.setTypeface(Fonts.roboto(context, m3 ? Fonts.REGULAR : Fonts.MEDIUM));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, m3 ? 24 : 20);
            t.setTextColor(c.onSurface);
            if (m3 && b.icon != null) t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setPadding(pad, m3 && b.icon != null ? dp(16) : pad, pad, dp(m3 ? 16 : 20));
            root.addView(t);
        }
        LinearLayout body = vbox();
        if (b.message != null) {
            TextView m = new TextView(context);
            m.setText(b.message);
            m.setTypeface(Fonts.roboto(context, Fonts.REGULAR));
            m.setTextSize(TypedValue.COMPLEX_UNIT_SP, m3 ? 14 : 16);
            m.setTextColor(c.onSurfaceVariant);
            m.setLineSpacing(0, m3 ? 1.1f : 1.15f);
            m.setPadding(pad, b.title == null ? pad : 0, pad, dp(m3 ? 8 : 8));
            body.addView(m);
        }
        if (b.items != null) {
            for (int i = 0; i < b.items.length; i++) {
                final int index = i;
                LinearLayout item = hbox();
                item.setMinimumHeight(dp(48));
                item.setPadding(b.itemsMode == DialogBuilder.ITEMS_PLAIN ? pad : dp(m3 ? 16 : 12), 0, pad, 0);
                Toggle tg = null;
                if (b.itemsMode == DialogBuilder.ITEMS_SINGLE) {
                    tg = toggle(RADIO).passive();
                    tg.setChecked(i == b.checkedItem);
                    item.addView(tg);
                    item.addView(space(m3 ? 4 : 8));
                } else if (b.itemsMode == DialogBuilder.ITEMS_MULTI) {
                    tg = toggle(CHECKBOX).passive();
                    tg.setChecked(b.checkedItems[i]);
                    item.addView(tg);
                    item.addView(space(m3 ? 4 : 8));
                }
                TextView t = text(T_SUBTITLE, b.items[i]);
                item.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                item.setBackgroundDrawable(touch(c.onSurface, null, 0));
                item.setClickable(true);
                final Toggle toggle = tg;
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
                            ViewGroup parent = (ViewGroup) v.getParent();
                            for (int j = 0; j < parent.getChildCount(); j++) {
                                View row = parent.getChildAt(j);
                                if (row instanceof ViewGroup && ((ViewGroup) row).getChildAt(0) instanceof Toggle) {
                                    ((Toggle) ((ViewGroup) row).getChildAt(0)).setChecked(j == index);
                                }
                            }
                            if (b.positive != null) return;
                        }
                        if (b.itemListener != null) b.itemListener.onItem(index);
                        b.dismiss();
                    }
                });
                trackHotspot(item);
                body.addView(item, matchWrap());
            }
        }
        if (b.view != null) {
            LinearLayout.LayoutParams p = matchWrap();
            p.leftMargin = pad;
            p.rightMargin = pad;
            body.addView(b.view, p);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        // Buttons: right aligned text buttons (neutral on the left).
        LinearLayout bar = hbox();
        bar.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(m3 ? 24 : 8), dp(m3 ? 16 : 8), dp(m3 ? 24 : 8), dp(m3 ? 24 : 8));
        if (b.neutral != null) {
            bar.addView(dialogButton(b, b.neutral, b.onNeutral));
            View fill = new View(context);
            bar.addView(fill, new LinearLayout.LayoutParams(0, 1, 1));
        }
        if (b.negative != null) bar.addView(dialogButton(b, b.negative, b.onNegative));
        if (b.positive != null) {
            if (b.negative != null) bar.addView(space(8));
            bar.addView(dialogButton(b, b.positive, b.onPositive));
        }
        if (b.neutral != null || b.negative != null || b.positive != null) {
            root.addView(bar, matchWrap());
        } else {
            root.addView(space(m3 ? 16 : 8));
        }
        Dialog d = rawDialog(root, b.cancelable, m3 ? 0.32f : 0.6f, m3 ? 560 : 400);
        if (Sdk.atLeast(21)) Draw.elevate(root, dp(m3 ? 6 : 24), r);
        holder[0] = d;
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
        return button(label, B_TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!b.keepOpen) b.dismiss();
                if (r != null) r.run();
            }
        });
    }

    // ---------------------------------------------------------------- logo

    @Override
    public Drawable logo(int sizePx) {
        if (m3) return new LogoDrawable(sizePx, c.primary, c.primaryContainer, c.onPrimaryContainer);
        // Material 1 product icon: flat primary disc, darker rim, white glyph.
        return new LogoDrawable(sizePx, c.primaryDark, c.primary, 0xFFFFFFFF);
    }

    protected IconDrawable navIcon(Ic ic, int color) {
        return icon(ic, color, 24);
    }
}
