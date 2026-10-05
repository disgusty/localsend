package com.disgusty.oldysend.ui;

import android.annotation.TargetApi;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.text.method.PasswordTransformationMethod;
import android.widget.TextView;

import com.disgusty.oldysend.util.Sdk;

/** Widget APIs that appeared after Android 1.0. */
public final class Compat {
    private Compat() {
    }

    /** TextView.setInputType is API 3; Android 1.0/1.1 get the equivalent key listener / transformation. */
    public static void inputType(TextView v, int type) {
        if (Sdk.atLeast(3)) {
            Api3.inputType(v, type);
            return;
        }
        int cls = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_NUMBER) v.setKeyListener(DigitsKeyListener.getInstance());
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD) v.setTransformationMethod(PasswordTransformationMethod.getInstance());
        v.setSingleLine((type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
    }

    /** View.setContentDescription is API 4. */
    public static void description(android.view.View v, CharSequence text) {
        if (Sdk.atLeast(4)) Api4.description(v, text);
    }

    /** PopupWindow.setOutsideTouchable is API 3; before that popups are dismissed by their own items / back. */
    public static void outsideTouchable(android.widget.PopupWindow pw) {
        if (Sdk.atLeast(3)) Api3.outside(pw);
    }

    /** HorizontalScrollView is API 3; Android 1.0/1.1 get the content without scrolling. */
    public static android.view.View hscroll(android.content.Context c, android.view.View content) {
        if (Sdk.atLeast(3)) return Api3.hscroll(c, content);
        return content;
    }

    @TargetApi(3)
    private static final class Api3 {
        static void inputType(TextView v, int type) {
            v.setInputType(type);
        }

        static void outside(android.widget.PopupWindow pw) {
            pw.setOutsideTouchable(true);
        }

        static android.view.View hscroll(android.content.Context c, android.view.View content) {
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(content);
            return hs;
        }
    }

    @TargetApi(4)
    private static final class Api4 {
        static void description(android.view.View v, CharSequence text) {
            v.setContentDescription(text);
        }
    }
}
