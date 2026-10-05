package com.disgusty.oldysend.ui.kit;

import android.content.Context;
import android.graphics.Typeface;

import com.disgusty.oldysend.i18n.I18n;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Sdk;

import java.util.HashMap;
import java.util.Map;

/**
 * Era-correct typefaces: Droid Sans for Classic (Android 1.x–3.x system font), Roboto for Holo (4.x) and
 * Material. System fonts are used when they already are the right ones, and for scripts the bundled
 * fonts do not cover (Roboto and Droid Sans cover Latin, Cyrillic and Greek).
 */
public final class Fonts {
    public static final int REGULAR = 0;
    public static final int MEDIUM = 1;
    public static final int BOLD = 2;

    private static final Map<String, Typeface> CACHE = new HashMap<String, Typeface>();

    private Fonts() {
    }

    private static boolean bundledCoversLocale() {
        I18n t = I18n.get();
        String id = t == null ? "en" : t.localeId();
        String[] other = {"ar", "arc", "bn", "fa", "gu", "he", "hi", "hy", "ja", "km", "ko", "lo", "ml", "ne", "si", "ta", "th", "ur", "zh"};
        for (String o : other) if (id.startsWith(o)) return false;
        return true;
    }

    private static Typeface asset(Context c, String name) {
        synchronized (CACHE) {
            if (CACHE.containsKey(name)) return CACHE.get(name);
            Typeface tf = null;
            try {
                tf = Typeface.createFromAsset(c.getAssets(), "fonts/" + name);
            } catch (Throwable e) {
                Log.w("Font " + name + " unavailable", e);
            }
            CACHE.put(name, tf);
            return tf;
        }
    }

    /**
     * Aramaic is written in Syriac script, which Android ships a font for only in recent versions: there the bundled
     * Noto Sans Syriac (SIL Open Font License) is used; Latin text falls back to the system font.
     */
    private static Typeface syriac(Context c, int weight) {
        I18n t = I18n.get();
        if (t == null || !"arc".equals(t.localeId()) || systemHasSyriac()) return null;
        Typeface tf = asset(c, "NotoSansSyriac-Regular.ttf");
        if (tf == null || weight == REGULAR) return tf;
        return Typeface.create(tf, Typeface.BOLD);
    }

    private static Boolean systemSyriac;

    private static boolean systemHasSyriac() {
        if (systemSyriac == null) {
            boolean found = false;
            String[] files = new java.io.File("/system/fonts").list();
            if (files != null) for (String f : files) if (f.startsWith("NotoSansSyriac")) found = true;
            systemSyriac = found;
        }
        return systemSyriac;
    }

    public static Typeface classic(Context c, int weight) {
        Typeface sy = syriac(c, weight);
        if (sy != null) return sy;
        boolean bold = weight != REGULAR;
        if (Sdk.INT < 14 || !bundledCoversLocale()) return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        Typeface tf = asset(c, bold ? "DroidSans-Bold.ttf" : "DroidSans.ttf");
        return tf != null ? tf : (bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    public static Typeface roboto(Context c, int weight) {
        Typeface sy = syriac(c, weight);
        if (sy != null) return sy;
        if (!bundledCoversLocale()) {
            if (weight == MEDIUM && Sdk.INT >= 21) return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            return weight == REGULAR ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD;
        }
        if (weight == REGULAR) {
            if (Sdk.INT >= 14) return Typeface.DEFAULT;
            Typeface tf = asset(c, "Roboto-Regular.ttf");
            return tf != null ? tf : Typeface.DEFAULT;
        }
        if (weight == MEDIUM) {
            if (Sdk.INT >= 21) return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            Typeface tf = asset(c, "Roboto-Medium.ttf");
            return tf != null ? tf : Typeface.DEFAULT_BOLD;
        }
        if (Sdk.INT >= 14) return Typeface.DEFAULT_BOLD;
        Typeface tf = asset(c, "Roboto-Bold.ttf");
        return tf != null ? tf : Typeface.DEFAULT_BOLD;
    }
}
