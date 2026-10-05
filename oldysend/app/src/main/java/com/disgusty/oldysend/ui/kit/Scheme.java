package com.disgusty.oldysend.ui.kit;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;

import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.util.Sdk;

/** Resolved colors of the active style. Field names follow Material 3 roles; older styles fill them sensibly. */
public final class Scheme {
    public String style;
    public boolean dark;
    public boolean oled;

    public int background;
    public int onBackground;
    public int surface;
    public int onSurface;
    public int surfaceVariant;
    public int onSurfaceVariant;
    public int surfaceContainerLowest;
    public int surfaceContainerLow;
    public int surfaceContainer;
    public int surfaceContainerHigh;
    public int surfaceContainerHighest;
    public int textDisabled;
    public int primary;
    public int onPrimary;
    public int primaryContainer;
    public int onPrimaryContainer;
    public int primaryDark;
    public int secondary;
    public int onSecondary;
    public int secondaryContainer;
    public int onSecondaryContainer;
    public int tertiary;
    public int tertiaryContainer;
    public int onTertiaryContainer;
    public int accent;
    public int outline;
    public int outlineVariant;
    public int divider;
    public int error;
    public int onError;
    public int errorContainer;
    public int onErrorContainer;
    public int inverseSurface;
    public int inverseOnSurface;
    public int inversePrimary;
    public int scrim;
    public int appBar;
    public int onAppBar;
    public int statusBar;
    public int navigationBar;
    public boolean lightStatusIcons;
    /** List item pressed/focused highlight. */
    public int highlight;

    public static boolean systemDark(Context context) {
        if (!Sdk.atLeast(8)) return false;
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Whether the style should be dark: Classic defaults to dark like Android 2.x, others follow the system. */
    public static boolean resolveDark(Context context, Settings s, String style) {
        String b = s.brightness();
        if (Settings.BRIGHTNESS_DARK.equals(b)) return true;
        if (Settings.BRIGHTNESS_LIGHT.equals(b)) return false;
        if (Settings.STYLE_CLASSIC.equals(style)) return !Sdk.atLeast(8) || systemDark(context) || !Sdk.atLeast(29);
        if (Settings.STYLE_HOLO.equals(style)) return !Sdk.atLeast(29) || systemDark(context);
        return systemDark(context);
    }

    public static Scheme resolve(Context context, Settings s) {
        String style = s.effectiveStyle();
        boolean dark = resolveDark(context, s, style);
        boolean oled = dark && Settings.COLOR_OLED.equals(s.colorMode());
        boolean custom = Settings.COLOR_CUSTOM.equals(s.colorMode());
        int preset = Math.max(0, Math.min(Palettes.NAMES.length - 1, custom ? s.customColor() : 0));
        Scheme c = new Scheme();
        c.style = style;
        c.dark = dark;
        c.oled = oled;
        if (Settings.STYLE_MD3.equals(style)) {
            if (!custom && Sdk.atLeast(31)) Api31.dynamic(context, c, dark);
            else md3(c, Palettes.MD3[preset][dark ? 1 : 0]);
            c.appBar = c.surface;
            c.onAppBar = c.onSurface;
            c.statusBar = c.surface;
            c.navigationBar = c.surfaceContainer;
            c.lightStatusIcons = !dark;
            c.divider = c.outlineVariant;
            c.highlight = withAlpha(c.onSurface, 0.10f);
            c.accent = c.primary;
        } else if (Settings.STYLE_MD1.equals(style)) {
            md1(c, Palettes.MD1[preset], dark);
        } else if (Settings.STYLE_HOLO.equals(style)) {
            holo(c, dark);
        } else {
            classic(c, dark);
        }
        if (oled) {
            c.background = 0xFF000000;
            c.surface = 0xFF000000;
            c.surfaceContainerLowest = 0xFF000000;
            if (Settings.STYLE_MD3.equals(style) || Settings.STYLE_MD1.equals(style)) {
                c.statusBar = Settings.STYLE_MD3.equals(style) ? 0xFF000000 : c.statusBar;
                c.navigationBar = 0xFF000000;
                if (Settings.STYLE_MD3.equals(style)) c.appBar = 0xFF000000;
            }
        }
        return c;
    }

    private static void md3(Scheme c, int[] r) {
        c.primary = r[Palettes.PRIMARY];
        c.onPrimary = r[Palettes.ON_PRIMARY];
        c.primaryContainer = r[Palettes.PRIMARY_CONTAINER];
        c.onPrimaryContainer = r[Palettes.ON_PRIMARY_CONTAINER];
        c.secondary = r[Palettes.SECONDARY];
        c.onSecondary = r[Palettes.ON_SECONDARY];
        c.secondaryContainer = r[Palettes.SECONDARY_CONTAINER];
        c.onSecondaryContainer = r[Palettes.ON_SECONDARY_CONTAINER];
        c.tertiary = r[Palettes.TERTIARY];
        c.tertiaryContainer = r[Palettes.TERTIARY_CONTAINER];
        c.onTertiaryContainer = r[Palettes.ON_TERTIARY_CONTAINER];
        c.error = r[Palettes.ERROR];
        c.onError = r[Palettes.ON_ERROR];
        c.errorContainer = r[Palettes.ERROR_CONTAINER];
        c.onErrorContainer = r[Palettes.ON_ERROR_CONTAINER];
        c.surface = r[Palettes.SURFACE];
        c.background = r[Palettes.SURFACE];
        c.onSurface = r[Palettes.ON_SURFACE];
        c.onBackground = r[Palettes.ON_SURFACE];
        c.surfaceVariant = r[Palettes.SURFACE_VARIANT];
        c.onSurfaceVariant = r[Palettes.ON_SURFACE_VARIANT];
        c.outline = r[Palettes.OUTLINE];
        c.outlineVariant = r[Palettes.OUTLINE_VARIANT];
        c.inverseSurface = r[Palettes.INVERSE_SURFACE];
        c.inverseOnSurface = r[Palettes.INVERSE_ON_SURFACE];
        c.inversePrimary = r[Palettes.INVERSE_PRIMARY];
        c.surfaceContainerLowest = r[Palettes.SURFACE_CONTAINER_LOWEST];
        c.surfaceContainerLow = r[Palettes.SURFACE_CONTAINER_LOW];
        c.surfaceContainer = r[Palettes.SURFACE_CONTAINER];
        c.surfaceContainerHigh = r[Palettes.SURFACE_CONTAINER_HIGH];
        c.surfaceContainerHighest = r[Palettes.SURFACE_CONTAINER_HIGHEST];
        c.scrim = r[Palettes.SCRIM];
        c.textDisabled = withAlpha(c.onSurface, 0.38f);
    }

    /** Material Design 1 (2014): Theme.Material / Theme.Material.Light colors with a palette primary. */
    private static void md1(Scheme c, int[] p, boolean dark) {
        c.primary = p[0];
        c.primaryDark = p[1];
        c.accent = dark ? p[2] : p[3];
        c.onPrimary = 0xFFFFFFFF;
        // background_material_dark #303030 / light #fafafa; cards #424242 / #ffffff (values/colors_material.xml)
        c.background = dark ? 0xFF303030 : 0xFFFAFAFA;
        c.surface = dark ? 0xFF424242 : 0xFFFFFFFF;
        c.surfaceContainer = c.surface;
        c.surfaceContainerLow = c.surface;
        c.surfaceContainerHigh = c.surface;
        c.surfaceContainerHighest = dark ? 0xFF4D4D4D : 0xFFEEEEEE;
        c.surfaceContainerLowest = c.background;
        // primary_text_default_material_dark = white, secondary 70% (dark) / 87% & 54% black (light)
        c.onSurface = dark ? 0xFFFFFFFF : 0xDE000000;
        c.onBackground = c.onSurface;
        c.onSurfaceVariant = dark ? 0xB3FFFFFF : 0x8A000000;
        c.textDisabled = dark ? 0x4DFFFFFF : 0x61000000;
        c.divider = dark ? 0x1FFFFFFF : 0x1F000000;
        c.outline = c.divider;
        c.outlineVariant = c.divider;
        c.secondaryContainer = withAlpha(c.accent, 0.2f);
        c.onSecondaryContainer = c.onSurface;
        c.primaryContainer = c.primary;
        c.onPrimaryContainer = 0xFFFFFFFF;
        c.error = dark ? 0xFFFF5252 : 0xFFD32F2F;
        c.onError = 0xFFFFFFFF;
        c.appBar = p[0];
        c.onAppBar = 0xFFFFFFFF;
        c.statusBar = p[1];
        c.navigationBar = 0xFF000000;
        c.lightStatusIcons = false;
        c.highlight = dark ? 0x33FFFFFF : 0x1F000000;
        c.inverseSurface = dark ? 0xFFE0E0E0 : 0xFF323232;
        c.inverseOnSurface = dark ? 0xDE000000 : 0xFFFFFFFF;
        c.secondary = c.accent;
        c.tertiary = c.accent;
        c.scrim = 0x99000000;
    }

    /** Holo (Android 4.4 colors_holo.xml): Theme.Holo / Theme.Holo.Light with Holo blue. */
    private static void holo(Scheme c, boolean dark) {
        int holoBlue = 0xFF33B5E5;          // holo_blue_light
        int holoBlueDark = 0xFF0099CC;      // holo_blue_dark
        c.primary = dark ? holoBlue : holoBlueDark;
        c.accent = c.primary;
        c.onPrimary = 0xFFFFFFFF;
        c.background = dark ? 0xFF000000 : 0xFFF3F3F3; // background_holo_dark / background_holo_light
        c.surface = dark ? 0xFF282828 : 0xFFFFFFFF;
        c.surfaceContainer = c.surface;
        c.surfaceContainerLow = c.surface;
        c.surfaceContainerHigh = c.surface;
        c.surfaceContainerHighest = dark ? 0xFF3A3A3A : 0xFFE5E5E5;
        c.surfaceContainerLowest = c.background;
        c.onSurface = dark ? 0xFFFFFFFF : 0xFF000000;   // primary_text_holo_dark/light
        c.onBackground = c.onSurface;
        c.onSurfaceVariant = dark ? 0xFFBEBEBE : 0xFF323232; // secondary_text_holo
        c.textDisabled = dark ? 0xFF4C4C4C : 0xFFB2B2B2;
        c.divider = dark ? 0x3DFFFFFF : 0x2E000000;
        c.outline = c.divider;
        c.outlineVariant = c.divider;
        c.primaryContainer = 0x6633B5E5;
        c.onPrimaryContainer = c.onSurface;
        c.secondaryContainer = c.primaryContainer;
        c.onSecondaryContainer = c.onSurface;
        c.error = 0xFFFF4444;               // holo_red_light
        c.onError = 0xFFFFFFFF;
        c.appBar = dark ? 0xFF222222 : 0xFFD9D9D9;
        c.onAppBar = c.onSurface;
        c.statusBar = 0xFF000000;
        c.navigationBar = 0xFF000000;
        c.highlight = 0x9933B5E5;           // list_pressed_holo
        c.inverseSurface = dark ? 0xFFEEEEEE : 0xFF222222;
        c.inverseOnSurface = dark ? 0xFF000000 : 0xFFFFFFFF;
        c.secondary = c.primary;
        c.tertiary = c.primary;
        c.scrim = 0x99000000;
    }

    /** Android 2.x Theme / Theme.Light (core/res/res/values/colors.xml of 2.3). */
    private static void classic(Scheme c, boolean dark) {
        int orange = 0xFFFFA200;
        c.primary = orange;
        c.accent = orange;
        c.onPrimary = 0xFF000000;
        c.background = dark ? 0xFF000000 : 0xFFFFFFFF;
        c.surface = dark ? 0xFF000000 : 0xFFFFFFFF;
        c.surfaceContainer = dark ? 0xFF2B2B2B : 0xFFEFEFEF;
        c.surfaceContainerLow = c.surface;
        c.surfaceContainerHigh = c.surfaceContainer;
        c.surfaceContainerHighest = dark ? 0xFF424242 : 0xFFDDDDDD;
        c.surfaceContainerLowest = c.background;
        c.onSurface = dark ? 0xFFFFFFFF : 0xFF000000;
        c.onBackground = c.onSurface;
        c.onSurfaceVariant = dark ? 0xFFBEBEBE : 0xFF323232;  // dim_foreground_dark / light
        c.textDisabled = dark ? 0xFF808080 : 0xFF808080;
        c.divider = dark ? 0xFF4C4C4C : 0xFFCCCCCC;
        c.outline = c.divider;
        c.outlineVariant = c.divider;
        c.primaryContainer = 0x66FFA200;
        c.onPrimaryContainer = c.onSurface;
        c.secondaryContainer = c.primaryContainer;
        c.onSecondaryContainer = c.onSurface;
        c.error = 0xFFFF0000;
        c.onError = 0xFFFFFFFF;
        c.appBar = 0xFF6D6D6D;
        c.onAppBar = 0xFFFFFFFF;
        c.statusBar = 0xFF000000;
        c.navigationBar = 0xFF000000;
        c.highlight = orange;
        c.inverseSurface = 0xFF333333;
        c.inverseOnSurface = 0xFFFFFFFF;
        c.secondary = orange;
        c.tertiary = orange;
        c.scrim = 0x99000000;
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Draws {@code over} with {@code alpha} on {@code base} (both opaque results). */
    public static int blend(int base, int over, float alpha) {
        int r = Math.round(((base >> 16) & 0xFF) * (1 - alpha) + ((over >> 16) & 0xFF) * alpha);
        int g = Math.round(((base >> 8) & 0xFF) * (1 - alpha) + ((over >> 8) & 0xFF) * alpha);
        int b = Math.round((base & 0xFF) * (1 - alpha) + (over & 0xFF) * alpha);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Material 3 dynamic color: Monet system palettes mapped to roles like MDC's DynamicColors. */
    @TargetApi(31)
    private static final class Api31 {
        static int col(Context ctx, int id) {
            return ctx.getResources().getColor(id, null);
        }

        static void dynamic(Context x, Scheme c, boolean dark) {
            int a1_0 = col(x, android.R.color.system_accent1_0), a1_100 = col(x, android.R.color.system_accent1_100),
                    a1_200 = col(x, android.R.color.system_accent1_200), a1_600 = col(x, android.R.color.system_accent1_600),
                    a1_700 = col(x, android.R.color.system_accent1_700), a1_800 = col(x, android.R.color.system_accent1_800),
                    a1_900 = col(x, android.R.color.system_accent1_900);
            int a2_0 = col(x, android.R.color.system_accent2_0), a2_100 = col(x, android.R.color.system_accent2_100),
                    a2_200 = col(x, android.R.color.system_accent2_200), a2_600 = col(x, android.R.color.system_accent2_600),
                    a2_700 = col(x, android.R.color.system_accent2_700), a2_800 = col(x, android.R.color.system_accent2_800),
                    a2_900 = col(x, android.R.color.system_accent2_900);
            int a3_100 = col(x, android.R.color.system_accent3_100), a3_200 = col(x, android.R.color.system_accent3_200),
                    a3_600 = col(x, android.R.color.system_accent3_600), a3_700 = col(x, android.R.color.system_accent3_700),
                    a3_900 = col(x, android.R.color.system_accent3_900);
            int n1_0 = col(x, android.R.color.system_neutral1_0), n1_10 = col(x, android.R.color.system_neutral1_10),
                    n1_50 = col(x, android.R.color.system_neutral1_50), n1_100 = col(x, android.R.color.system_neutral1_100),
                    n1_800 = col(x, android.R.color.system_neutral1_800), n1_900 = col(x, android.R.color.system_neutral1_900),
                    n1_1000 = col(x, android.R.color.system_neutral1_1000);
            int n2_100 = col(x, android.R.color.system_neutral2_100), n2_200 = col(x, android.R.color.system_neutral2_200),
                    n2_400 = col(x, android.R.color.system_neutral2_400), n2_500 = col(x, android.R.color.system_neutral2_500),
                    n2_700 = col(x, android.R.color.system_neutral2_700);
            if (!dark) {
                c.primary = a1_600; c.onPrimary = a1_0; c.primaryContainer = a1_100; c.onPrimaryContainer = a1_900;
                c.secondary = a2_600; c.onSecondary = a2_0; c.secondaryContainer = a2_100; c.onSecondaryContainer = a2_900;
                c.tertiary = a3_600; c.tertiaryContainer = a3_100; c.onTertiaryContainer = a3_900;
                c.surface = n1_10; c.onSurface = n1_900; c.surfaceVariant = n2_100; c.onSurfaceVariant = n2_700;
                c.outline = n2_500; c.outlineVariant = n2_200; c.inverseSurface = n1_800; c.inverseOnSurface = n1_50;
                c.inversePrimary = a1_200;
                c.surfaceContainerLowest = n1_0;
                c.surfaceContainerLow = blend(n1_50, n1_10, 0.25f);   // tone 96
                c.surfaceContainer = blend(n1_100, n1_50, 0.8f);      // tone 94
                c.surfaceContainerHigh = blend(n1_100, n1_50, 0.4f);  // tone 92
                c.surfaceContainerHighest = n1_100;                   // tone 90
                c.error = 0xFFBA1A1A; c.onError = 0xFFFFFFFF; c.errorContainer = 0xFFFFDAD6; c.onErrorContainer = 0xFF410002;
            } else {
                c.primary = a1_200; c.onPrimary = a1_800; c.primaryContainer = a1_700; c.onPrimaryContainer = a1_100;
                c.secondary = a2_200; c.onSecondary = a2_800; c.secondaryContainer = a2_700; c.onSecondaryContainer = a2_100;
                c.tertiary = a3_200; c.tertiaryContainer = a3_700; c.onTertiaryContainer = a3_100;
                c.surface = n1_900; c.onSurface = n1_100; c.surfaceVariant = n2_700; c.onSurfaceVariant = n2_200;
                c.outline = n2_400; c.outlineVariant = n2_700; c.inverseSurface = n1_100; c.inverseOnSurface = n1_800;
                c.inversePrimary = a1_600;
                c.surfaceContainerLowest = blend(n1_1000, n1_900, 0.4f); // tone 4
                c.surfaceContainerLow = n1_900;                          // tone 10
                c.surfaceContainer = blend(n1_900, n1_800, 0.2f);        // tone 12
                c.surfaceContainerHigh = blend(n1_900, n1_800, 0.7f);    // tone 17
                c.surfaceContainerHighest = blend(n1_800, n1_100, 0.03f); // tone 22
                c.error = 0xFFFFB4AB; c.onError = 0xFF690005; c.errorContainer = 0xFF93000A; c.onErrorContainer = 0xFFFFDAD6;
            }
            c.background = c.surface;
            c.onBackground = c.onSurface;
            c.scrim = 0xFF000000;
            c.textDisabled = withAlpha(c.onSurface, 0.38f);
        }
    }
}
