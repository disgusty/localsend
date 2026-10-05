package com.disgusty.oldysend.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.util.Log;

/**
 * Switches the launcher icon to the interface style: the manifest declares one activity-alias per style
 * (".LauncherClassic", ".LauncherHolo", ".LauncherMd1", ".LauncherMd3") and exactly one is enabled.
 * PackageManager.setComponentEnabledSetting exists since Android 1.0.
 */
public final class Launcher {
    private static final String[] STYLES = {Settings.STYLE_CLASSIC, Settings.STYLE_HOLO, Settings.STYLE_MD1, Settings.STYLE_MD3};
    private static final String[] ALIASES = {".LauncherClassic", ".LauncherHolo", ".LauncherMd1", ".LauncherMd3"};

    private Launcher() {
    }

    /** The style whose icon the launcher should show. */
    public static String wanted(Settings s) {
        return s.iconFollowsStyle() ? s.effectiveStyle() : Settings.autoStyle();
    }

    /**
     * Enables the alias of {@link #wanted} and disables the others. Some launchers restart or drop the home screen
     * shortcut when the component changes, so callers run this while the app is in the background.
     */
    public static void sync(Context context, Settings s) {
        String want = wanted(s);
        PackageManager pm = context.getPackageManager();
        String pkg = context.getPackageName();
        try {
            // Enable first, so there is never a moment without any launcher entry.
            for (int i = 0; i < STYLES.length; i++) {
                if (STYLES[i].equals(want)) set(pm, new ComponentName(pkg, pkg + ALIASES[i]), true);
            }
            for (int i = 0; i < STYLES.length; i++) {
                if (!STYLES[i].equals(want)) set(pm, new ComponentName(pkg, pkg + ALIASES[i]), false);
            }
        } catch (Exception e) {
            Log.w("Could not switch the launcher icon", e);
        }
    }

    private static void set(PackageManager pm, ComponentName c, boolean enabled) {
        int state = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        int current = pm.getComponentEnabledSetting(c);
        if (current == state) return;
        // DEFAULT means "as in the manifest", which is the automatic style's alias (values*/launcher.xml).
        if (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && defaultEnabled(c) == enabled) return;
        pm.setComponentEnabledSetting(c, state, PackageManager.DONT_KILL_APP);
    }

    private static boolean defaultEnabled(ComponentName c) {
        String cls = c.getClassName();
        String style = Settings.autoStyle();
        for (int i = 0; i < STYLES.length; i++) {
            if (cls.endsWith(ALIASES[i])) return STYLES[i].equals(style);
        }
        return false;
    }
}
