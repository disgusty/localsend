package com.disgusty.oldysend;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;

import com.disgusty.oldysend.files.SendItem;
import com.disgusty.oldysend.i18n.I18n;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.proto.ReceiveController;
import com.disgusty.oldysend.proto.SendController;
import com.disgusty.oldysend.service.Notifs;
import com.disgusty.oldysend.service.ServerService;
import com.disgusty.oldysend.ui.BaseActivity;
import com.disgusty.oldysend.ui.Launcher;
import com.disgusty.oldysend.ui.ProgressActivity;
import com.disgusty.oldysend.ui.ReceiveActivity;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Sdk;

import java.util.ArrayList;
import java.util.List;

public final class App extends Application implements Core.Ui {
    private static App instance;

    /** Bumped when style, colors or language change so open activities rebuild themselves. */
    public static int appearanceVersion;

    /** The send selection (Send tab), shared by all screens. */
    public static final List<SendItem> selection = new ArrayList<SendItem>();

    private BaseActivity foreground;
    private boolean transferActive;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Log.e("Crash in " + t.getName(), e);
                if (previous != null) previous.uncaughtException(t, e);
            }
        });
        Core core = Core.init(this);
        I18n.load(this, core.settings.locale());
        core.ui = this;
        Notifs.init(this);
        Log.i("OldySend " + BuildConfig.VERSION_NAME + " on Android " + Sdk.release() + " (API " + Sdk.INT + ")");
        core.startServer();
        if (core.settings.keepRunning()) ServerService.update(this);
    }

    public static App get() {
        return instance;
    }

    public static Core core() {
        return Core.get();
    }

    public static void appearanceChanged() {
        appearanceVersion++;
    }

    public static void reloadLanguage() {
        I18n.load(instance, Core.get().settings.locale());
        appearanceChanged();
    }

    // ---- foreground tracking ----

    public void setForeground(BaseActivity a) {
        foreground = a;
    }

    public void clearForeground(BaseActivity a) {
        if (foreground != a) return;
        foreground = null;
        // Switch the launcher icon only once the app has left the screen: changing the launcher component
        // makes some launchers (and some Android versions) close the app's task.
        Core.get().main.removeCallbacks(syncLauncher);
        Core.get().main.postDelayed(syncLauncher, 1500);
    }

    private final Runnable syncLauncher = new Runnable() {
        @Override
        public void run() {
            if (foreground == null) Launcher.sync(App.this, Core.get().settings);
        }
    };

    public BaseActivity foreground() {
        return foreground;
    }

    public boolean transferActive() {
        return transferActive;
    }

    // ---- Core.Ui ----

    @Override
    public void showReceiveRequest() {
        Core.get().runOnMain(new Runnable() {
            @Override
            public void run() {
                ReceiveController.Session s = Core.get().receive.session();
                if (s == null) return;
                if (foreground != null) {
                    startActivity(new Intent(App.this, ReceiveActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else {
                    Notifs.showRequest(App.this, s);
                    // Before Android 10 apps may still bring up an activity from the background.
                    if (!Sdk.atLeast(29)) {
                        startActivity(new Intent(App.this, ReceiveActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                }
            }
        });
    }

    @Override
    public void showReceiveProgress() {
        Core.get().runOnMain(new Runnable() {
            @Override
            public void run() {
                Notifs.cancelRequest(App.this);
                ReceiveController.Session s = Core.get().receive.session();
                if (s == null) return;
                Activity a = foreground;
                if (a instanceof ReceiveActivity) {
                    // The receive page replaces itself with the progress page.
                    return;
                }
                if (a != null || !Sdk.atLeast(29)) {
                    startActivity(new Intent(App.this, ProgressActivity.class).putExtra(ProgressActivity.EXTRA_RECEIVE, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
            }
        });
    }

    private final Runnable stopTransfer = new Runnable() {
        @Override
        public void run() {
            transferActive = false;
            ServerService.update(App.this);
        }
    };

    @Override
    public void transferActive(final boolean active) {
        Core.get().runOnMain(new Runnable() {
            @Override
            public void run() {
                Core.get().main.removeCallbacks(stopTransfer);
                if (active) {
                    if (transferActive) return;
                    transferActive = true;
                    ServerService.update(App.this);
                } else {
                    // Short transfers end within milliseconds; let the service reach startForeground() first.
                    Core.get().main.postDelayed(stopTransfer, 3000);
                }
            }
        });
    }

    @Override
    public void requestSendPin(final SendController.Session session) {
        Core.get().runOnMain(new Runnable() {
            @Override
            public void run() {
                if (foreground != null) foreground.askSendPin(session);
                else Core.get().send.submitPin(session, null);
            }
        });
    }
}
