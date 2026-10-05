package com.disgusty.oldysend.ui.kit;

import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** List row builder: [icon] title / summary [trailing]. Metrics come from the style. */
public class Row {
    public static final class Metrics {
        public int minHeightOneLine;
        public int minHeightTwoLine;
        public int paddingStart;
        public int paddingEnd;
        public int paddingVertical;
        public int iconSize;
        public int iconGap;
        public int titleRole = Kit.T_SUBTITLE;
        public int summaryRole = Kit.T_SECONDARY;
    }

    protected final Kit k;
    protected final Metrics m;
    protected Drawable icon;
    protected View iconView;
    protected CharSequence title;
    protected CharSequence summary;
    protected View trailing;
    protected View.OnClickListener click;
    protected View.OnLongClickListener longClick;
    protected int summaryLines = 2;
    protected boolean enabled = true;
    public TextView titleView;
    public TextView summaryView;

    public Row(Kit k, Metrics m) {
        this.k = k;
        this.m = m;
    }

    public Row icon(Drawable d) {
        icon = d;
        return this;
    }

    public Row icon(Ic ic) {
        icon = k.listIcon(ic);
        return this;
    }

    /** Custom leading view (thumbnails, device icons). */
    public Row leading(View v) {
        iconView = v;
        return this;
    }

    public Row title(CharSequence s) {
        title = s;
        return this;
    }

    public Row summary(CharSequence s) {
        summary = s;
        return this;
    }

    public Row summaryLines(int lines) {
        summaryLines = lines;
        return this;
    }

    public Row trailing(View v) {
        trailing = v;
        return this;
    }

    public Row onClick(View.OnClickListener l) {
        click = l;
        return this;
    }

    public Row onLongClick(View.OnLongClickListener l) {
        longClick = l;
        return this;
    }

    public Row enabled(boolean e) {
        enabled = e;
        return this;
    }

    public View build() {
        LinearLayout row = k.hbox();
        boolean two = summary != null && summary.length() > 0;
        row.setMinimumHeight(two ? m.minHeightTwoLine : m.minHeightOneLine);
        boolean rtl = Kit.rtl();
        row.setPadding(rtl ? m.paddingEnd : m.paddingStart, m.paddingVertical, rtl ? m.paddingStart : m.paddingEnd, m.paddingVertical);
        if (iconView != null || icon != null) {
            View iv = iconView;
            if (iv == null) {
                ImageView img = new ImageView(k.context);
                img.setImageDrawable(icon);
                img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                iv = img;
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(m.iconSize, m.iconSize);
            if (rtl) p.leftMargin = m.iconGap;
            else p.rightMargin = m.iconGap;
            row.addView(iv, p);
        }
        LinearLayout texts = k.vbox();
        texts.setGravity(Gravity.CENTER_VERTICAL);
        titleView = k.text(m.titleRole, title == null ? "" : title);
        k.ellipsize(titleView, 2);
        texts.addView(titleView);
        if (two) {
            summaryView = k.text(m.summaryRole, summary);
            k.ellipsize(summaryView, summaryLines);
            texts.addView(summaryView);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (trailing != null) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rtl) p.rightMargin = k.dp(8);
            else p.leftMargin = k.dp(8);
            row.addView(trailing, p);
        }
        if (click != null || longClick != null) {
            row.setBackgroundDrawable(k.listSelector());
            row.setClickable(true);
            row.setFocusable(true);
            if (click != null) row.setOnClickListener(click);
            if (longClick != null) row.setOnLongClickListener(longClick);
        }
        if (!enabled) {
            row.setEnabled(false);
            titleView.setTextColor(k.textColor(-1));
            if (summaryView != null) summaryView.setTextColor(k.textColor(-1));
        }
        return row;
    }
}
