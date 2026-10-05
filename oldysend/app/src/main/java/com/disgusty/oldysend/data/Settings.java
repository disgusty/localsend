package com.disgusty.oldysend.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.disgusty.oldysend.i18n.I18n;
import com.disgusty.oldysend.util.Codec;
import com.disgusty.oldysend.util.Sdk;
import com.disgusty.oldysend.util.Text;

import java.util.ArrayList;
import java.util.List;

/** All persisted preferences. SharedPreferences exists since API 1; apply() (API 9) is avoided. */
public final class Settings {
    public static final int DEFAULT_PORT = 53317;
    public static final String DEFAULT_MULTICAST = "224.0.0.167";
    public static final int DEFAULT_DISCOVERY_TIMEOUT = 500;

    public static final String STYLE_AUTO = "auto";
    public static final String STYLE_CLASSIC = "classic";
    public static final String STYLE_HOLO = "holo";
    public static final String STYLE_MD1 = "md1";
    public static final String STYLE_MD3 = "md3";

    public static final String BRIGHTNESS_SYSTEM = "system";
    public static final String BRIGHTNESS_LIGHT = "light";
    public static final String BRIGHTNESS_DARK = "dark";

    public static final String COLOR_SYSTEM = "system";
    public static final String COLOR_OLED = "oled";
    public static final String COLOR_CUSTOM = "custom";

    public static final String QUICK_SAVE_OFF = "off";
    public static final String QUICK_SAVE_FAVORITES = "favorites";
    public static final String QUICK_SAVE_ON = "on";

    public static final String SEND_SINGLE = "single";
    public static final String SEND_MULTIPLE = "multiple";
    public static final String SEND_LINK = "link";

    public static final String DEVICE_MOBILE = "mobile";

    private final SharedPreferences prefs;

    public Settings(Context context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (!prefs.contains("alias")) {
            SharedPreferences.Editor e = prefs.edit();
            e.putString("alias", "");
            e.putString("httpFingerprint", Codec.randomId());
            e.commit();
        }
    }

    private String s(String key, String def) {
        return prefs.getString(key, def);
    }

    private boolean b(String key, boolean def) {
        return prefs.getBoolean(key, def);
    }

    private int i(String key, int def) {
        return prefs.getInt(key, def);
    }

    private void put(String key, String value) {
        prefs.edit().putString(key, value).commit();
    }

    private void put(String key, boolean value) {
        prefs.edit().putBoolean(key, value).commit();
    }

    private void put(String key, int value) {
        prefs.edit().putInt(key, value).commit();
    }

    // ---- Appearance ----

    /** Interface style chosen in the settings; {@link #STYLE_AUTO} follows the Android version. */
    public String style() {
        return s("style", STYLE_AUTO);
    }

    public void setStyle(String v) {
        put("style", v);
    }

    /** The style actually used: Classic before 3.0, Holo for 3.0–4.4, Material 1 for 5–11, Material 3 for 12+. */
    public String effectiveStyle() {
        String st = style();
        if (!STYLE_AUTO.equals(st)) return st;
        return autoStyle();
    }

    /** Whether the launcher icon follows the chosen style (otherwise it stays the Android version's own style). */
    public boolean iconFollowsStyle() {
        return b("iconFollowsStyle", true);
    }

    public void setIconFollowsStyle(boolean v) {
        put("iconFollowsStyle", v);
    }

    public static String autoStyle() {
        if (Sdk.INT >= 31) return STYLE_MD3;
        if (Sdk.INT >= 21) return STYLE_MD1;
        if (Sdk.INT >= 11) return STYLE_HOLO;
        return STYLE_CLASSIC;
    }

    public String brightness() {
        return s("brightness", BRIGHTNESS_SYSTEM);
    }

    public void setBrightness(String v) {
        put("brightness", v);
    }

    public String colorMode() {
        return s("colorMode", COLOR_SYSTEM);
    }

    public void setColorMode(String v) {
        put("colorMode", v);
    }

    /** Index into the style's preset palette list (used when colorMode is custom). */
    public int customColor() {
        return i("customColor", 0);
    }

    public void setCustomColor(int v) {
        put("customColor", v);
    }

    public String locale() {
        return s("locale", I18n.SYSTEM);
    }

    public void setLocale(String v) {
        put("locale", v);
    }

    public boolean animations() {
        return b("animations", true);
    }

    public void setAnimations(boolean v) {
        put("animations", v);
    }

    // ---- Receive ----

    public String quickSave() {
        if (b("quickSave", false)) return QUICK_SAVE_ON;
        if (b("quickSaveFromFavorites", true)) return QUICK_SAVE_FAVORITES;
        return QUICK_SAVE_OFF;
    }

    public void setQuickSave(String v) {
        prefs.edit()
                .putBoolean("quickSave", QUICK_SAVE_ON.equals(v))
                .putBoolean("quickSaveFromFavorites", !QUICK_SAVE_OFF.equals(v))
                .commit();
    }

    public boolean quickSaveOn() {
        return b("quickSave", false);
    }

    public void setQuickSaveOn(boolean v) {
        put("quickSave", v);
    }

    public boolean quickSaveFromFavorites() {
        return b("quickSaveFromFavorites", true);
    }

    public void setQuickSaveFromFavorites(boolean v) {
        put("quickSaveFromFavorites", v);
    }

    /** Receive PIN, or null when no PIN is required. */
    public String receivePin() {
        String pin = s("receivePin", null);
        return Text.isEmpty(pin) ? null : pin;
    }

    public void setReceivePin(String v) {
        put("receivePin", v == null ? "" : v);
    }

    public boolean autoFinish() {
        return b("autoFinish", false);
    }

    public void setAutoFinish(boolean v) {
        put("autoFinish", v);
    }

    /** Destination folder: null = Downloads, a file path, or a SAF tree uri ("content://..."). */
    public String destination() {
        String d = s("destination", null);
        return Text.isEmpty(d) ? null : d;
    }

    public void setDestination(String v) {
        put("destination", v == null ? "" : v);
    }

    public boolean saveToGallery() {
        return b("saveToGallery", true);
    }

    public void setSaveToGallery(boolean v) {
        put("saveToGallery", v);
    }

    public boolean saveToHistory() {
        return b("saveToHistory", true);
    }

    public void setSaveToHistory(boolean v) {
        put("saveToHistory", v);
    }

    public boolean verifyChecksums() {
        return b("verifyChecksums", false);
    }

    public void setVerifyChecksums(boolean v) {
        put("verifyChecksums", v);
    }

    // ---- Send ----

    public boolean shareViaLinkAutoAccept() {
        return b("shareViaLinkAutoAccept", false);
    }

    public void setShareViaLinkAutoAccept(boolean v) {
        put("shareViaLinkAutoAccept", v);
    }

    public boolean createChecksums() {
        return b("createChecksums", false);
    }

    public void setCreateChecksums(boolean v) {
        put("createChecksums", v);
    }

    public String sendMode() {
        return s("sendMode", SEND_SINGLE);
    }

    public void setSendMode(String v) {
        put("sendMode", v);
    }

    // ---- Network ----

    public String alias() {
        return s("alias", "");
    }

    public void setAlias(String v) {
        put("alias", v);
    }

    public String deviceType() {
        return s("deviceType", DEVICE_MOBILE);
    }

    public void setDeviceType(String v) {
        put("deviceType", v);
    }

    public String deviceModel() {
        String m = s("deviceModel", null);
        return m != null ? m : defaultDeviceModel();
    }

    public void setDeviceModel(String v) {
        put("deviceModel", v);
    }

    @SuppressWarnings("deprecation")
    public static String defaultDeviceModel() {
        String brand = Sdk.atLeast(4) ? Api4.manufacturer() : Build.BRAND;
        if (Text.isEmpty(brand) || "unknown".equals(brand)) brand = Build.MODEL;
        if (Text.isEmpty(brand)) return "Android";
        return Character.toUpperCase(brand.charAt(0)) + brand.substring(1);
    }

    public int port() {
        return i("port", DEFAULT_PORT);
    }

    public void setPort(int v) {
        put("port", v);
    }

    public String multicastGroup() {
        return s("multicastGroup", DEFAULT_MULTICAST);
    }

    public void setMulticastGroup(String v) {
        put("multicastGroup", v);
    }

    public int discoveryTimeout() {
        return i("discoveryTimeout", DEFAULT_DISCOVERY_TIMEOUT);
    }

    public void setDiscoveryTimeout(int v) {
        put("discoveryTimeout", v);
    }

    /** HTTPS needs AES-GCM cipher suites (what LocalSend's rustls accepts), available since Android 5.0. */
    public static boolean httpsSupported() {
        return Sdk.INT >= 21;
    }

    public boolean encryption() {
        return httpsSupported() && b("encryption", true);
    }

    public void setEncryption(boolean v) {
        put("encryption", v);
    }

    /** Interface names excluded from discovery and the server address list. */
    public List<String> networkBlacklist() {
        return list("networkBlacklist");
    }

    public void setNetworkBlacklist(List<String> v) {
        put("networkBlacklist", Text.join("\n", v));
    }

    public List<String> networkWhitelist() {
        return list("networkWhitelist");
    }

    public void setNetworkWhitelist(List<String> v) {
        put("networkWhitelist", Text.join("\n", v));
    }

    private List<String> list(String key) {
        List<String> out = new ArrayList<String>();
        String v = s(key, "");
        for (String p : v.split("\n")) if (p.trim().length() > 0) out.add(p.trim());
        return out;
    }

    public String httpFingerprint() {
        return s("httpFingerprint", "oldysend");
    }

    public boolean advancedSettings() {
        return b("advancedSettings", false);
    }

    public void setAdvancedSettings(boolean v) {
        put("advancedSettings", v);
    }

    /** OldySend extra: keep the server alive in the background with a persistent notification. */
    public boolean keepRunning() {
        return b("keepRunning", false);
    }

    public void setKeepRunning(boolean v) {
        put("keepRunning", v);
    }

    public String recentAddresses() {
        return s("recentAddresses", "");
    }

    public void setRecentAddresses(String v) {
        put("recentAddresses", v);
    }

    public boolean noticeShown(String key) {
        return b("notice_" + key, false);
    }

    public void setNoticeShown(String key) {
        put("notice_" + key, true);
    }

    public boolean multilineMessages() {
        return b("multilineMessages", true);
    }

    public void setMultilineMessages(boolean v) {
        put("multilineMessages", v);
    }

    @android.annotation.TargetApi(4)
    private static final class Api4 {
        static String manufacturer() {
            return Build.MANUFACTURER;
        }
    }
}
