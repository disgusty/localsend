package com.disgusty.oldysend.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;

import com.disgusty.oldysend.App;
import com.disgusty.oldysend.R;
import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.i18n.I18n;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.proto.SendController;
import com.disgusty.oldysend.ui.kit.Field;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.ui.kit.Screen;
import com.disgusty.oldysend.util.Sdk;

/** Common screen plumbing: style kit, core listener, menu key, rebuild on appearance changes. */
public abstract class BaseActivity extends Activity implements Core.Listener {
    protected Core core;
    protected Kit k;
    protected Screen screen;
    protected I18n t;
    private int appearance;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        core = App.core();
        t = I18n.get();
        appearance = App.appearanceVersion;
        boolean dark = com.disgusty.oldysend.ui.kit.Scheme.resolveDark(this, core.settings, core.settings.effectiveStyle());
        setTheme(dark ? R.style.OldyDark : R.style.OldyLight);
        super.onCreate(savedInstanceState);
        k = Kit.create(this);
        k.applyWindow();
        screen = k.screen();
        build();
        setContentView(screen.root);
        if (Sdk.atLeast(17)) Api17.direction(this, t.isRtl());
    }

    /** Builds the screen content into {@link #screen}. */
    protected abstract void build();

    /** Refreshes dynamic content; called on resume and on core changes. */
    protected void refresh(int what) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        App.get().setForeground(this);
        if (appearance != App.appearanceVersion) {
            rebuild();
            return;
        }
        core.addListener(this);
        refresh(-1);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        core.removeListener(this);
        App.get().clearForeground(this);
    }

    public boolean isResumedCompat() {
        return resumed;
    }

    @Override
    public void onCoreChanged(int what) {
        if (resumed) refresh(what);
    }

    /** Restarts the activity (Activity.recreate() is API 11). */
    @SuppressWarnings("deprecation")
    protected void rebuild() {
        Intent i = getIntent();
        finish();
        if (Sdk.atLeast(5)) overridePendingTransition(0, 0);
        startActivity(i);
        if (Sdk.atLeast(5)) overridePendingTransition(0, 0);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (k.onMenuKey(screen)) return true;
            if (screen.appBar != null && !screen.appBar.actions().isEmpty()) {
                screen.appBar.openMenu();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    protected String s(String key) {
        return t.text(key);
    }

    protected String s(String key, Object... params) {
        return t.text(key, params);
    }

    protected void open(Class<? extends Activity> cls) {
        startActivity(new Intent(this, cls));
    }

    protected Runnable finisher() {
        return new Runnable() {
            @Override
            public void run() {
                finish();
            }
        };
    }

    protected boolean isClassic() {
        return Settings.STYLE_CLASSIC.equals(k.style);
    }

    /** The receiver asks for a PIN (prepare-upload answered 401). */
    public void askSendPin(final SendController.Session session) {
        final Field f = k.field(null, false);
        com.disgusty.oldysend.ui.Compat.inputType(f.edit, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (session.pinWasWrong) f.setError(s("web.invalidPin"));
        final boolean[] answered = new boolean[1];
        k.dialog().title(s("dialogs.pin.title"))
                .view(f.view)
                .negative(s("general.cancel"), new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        core.send.submitPin(session, null);
                    }
                })
                .positive(s("general.confirm"), new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        core.send.submitPin(session, f.text());
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        if (!answered[0]) core.send.submitPin(session, null);
                    }
                })
                .show();
        f.edit.requestFocus();
    }

    @TargetApi(17)
    private static final class Api17 {
        static void direction(Activity a, boolean rtl) {
            a.getWindow().getDecorView().setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        }
    }
}
