package com.disgusty.oldysend.ui;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;

import com.disgusty.oldysend.App;
import com.disgusty.oldysend.files.SendItem;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.ui.kit.Action;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.NavBar;
import com.disgusty.oldysend.util.Sdk;

import java.util.ArrayList;
import java.util.List;

/** Receive / Send / Settings, like LocalSend's home page. */
public final class MainActivity extends BaseActivity {
    static final String EXTRA_TAB = "tab";
    static int lastTab;

    ReceiveTab receiveTab;
    SendTab sendTab;
    SettingsTab settingsTab;
    private int tab;

    @Override
    protected void build() {
        receiveTab = new ReceiveTab(this);
        sendTab = new SendTab(this);
        settingsTab = new SettingsTab(this);
        tab = getIntent().getIntExtra(EXTRA_TAB, lastTab);
        String[] labels = {s("receiveTab.title"), s("sendTab.title"), s("settingsTab.title")};
        Ic[] icons = {Ic.WIFI, Ic.SEND, Ic.SETTINGS};
        NavBar nav = k.navBar(labels, icons, new NavBar.Listener() {
            @Override
            public void onSelected(int index) {
                showTab(index);
            }
        });
        screen.setNav(nav);
        showTab(tab);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleShareIntent(getIntent());
        requestPermissions();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasExtra(EXTRA_TAB)) showTab(intent.getIntExtra(EXTRA_TAB, 0));
        handleShareIntent(intent);
    }

    void showTab(int index) {
        tab = index;
        lastTab = index;
        if (screen.nav() != null && screen.nav().selected() != index) screen.nav().select(index);
        View v;
        List<Action> actions = new ArrayList<Action>();
        switch (index) {
            case 1:
                v = sendTab.view();
                sendTab.actions(actions);
                break;
            case 2:
                v = settingsTab.view();
                settingsTab.actions(actions);
                break;
            default:
                v = receiveTab.view();
                receiveTab.actions(actions);
                break;
        }
        screen.appBar.title(index == 0 ? s("appName") : index == 1 ? s("sendTab.title") : s("settingsTab.title"));
        screen.appBar.actions(actions);
        screen.setContent(v);
        refresh(-1);
    }

    @Override
    protected void refresh(int what) {
        if (tab == 0) receiveTab.refresh(what);
        else if (tab == 1) sendTab.refresh(what);
        else settingsTab.refresh(what);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (sendTab.onResult(requestCode, resultCode, data)) return;
        settingsTab.onResult(requestCode, resultCode, data);
    }

    /**
     * Debug builds only: start a transfer from adb for automated protocol tests, e.g.
     * am start -n com.disgusty.oldysend/.ui.MainActivity --es debugSendTo 10.0.2.2 --ei debugPort 53317 --ez debugHttps true --ei debugBytes 100000 [--es debugDeadIp 10.0.2.99]
     */
    private boolean handleDebugSend(Intent intent) {
        if (!com.disgusty.oldysend.BuildConfig.DEBUG || intent == null || !intent.hasExtra("debugSendTo")) return false;
        String ip = intent.getStringExtra("debugSendTo");
        int port = intent.getIntExtra("debugPort", 53317);
        boolean https = intent.getBooleanExtra("debugHttps", false);
        int bytes = intent.getIntExtra("debugBytes", 0);
        App.selection.clear();
        if (bytes > 0) {
            try {
                java.io.File f = new java.io.File(getCacheDir(), "debug payload ✓.bin");
                java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                byte[] buf = new byte[bytes];
                new java.util.Random(42).nextBytes(buf);
                out.write(buf);
                out.close();
                App.selection.add(SendItem.ofFile(f, null));
            } catch (Exception e) {
                k.toast(e.toString());
            }
        } else {
            App.selection.add(SendItem.ofText("Hello from OldySend on Android " + com.disgusty.oldysend.util.Sdk.release()));
        }
        SendTab.debugDeadIp = intent.getStringExtra("debugDeadIp");
        intent.removeExtra("debugSendTo");
        showTab(1);
        sendTab.sendToAddress(ip, port, https, null);
        return true;
    }

    @SuppressWarnings("deprecation")
    private void handleShareIntent(Intent intent) {
        if (handleDebugSend(intent)) return;
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !"android.intent.action.SEND_MULTIPLE".equals(action)) return;
        List<SendItem> items = new ArrayList<SendItem>();
        try {
            if (Intent.ACTION_SEND.equals(action)) {
                Parcelable p = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (p instanceof Uri) items.add(SendItem.ofUri(this, (Uri) p, null));
                else {
                    CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                    if (text != null) items.add(SendItem.ofText(text.toString()));
                }
            } else {
                ArrayList<Parcelable> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (list != null) for (Parcelable p : list) if (p instanceof Uri) items.add(SendItem.ofUri(this, (Uri) p, null));
            }
        } catch (Exception e) {
            k.toast(s("general.error") + ": " + e.getMessage());
        }
        // Consume the intent so a rebuild does not add the files twice.
        intent.setAction(Intent.ACTION_MAIN);
        if (items.isEmpty()) return;
        App.selection.addAll(items);
        showTab(1);
        core.changed(Core.CHANGED_SEND);
    }

    private void requestPermissions() {
        if (!Sdk.atLeast(23)) return;
        List<String> needed = new ArrayList<String>();
        if (!Sdk.atLeast(29)) needed.add("android.permission.WRITE_EXTERNAL_STORAGE");
        else if (!Sdk.atLeast(33)) needed.add("android.permission.READ_EXTERNAL_STORAGE");
        if (Sdk.atLeast(33)) needed.add("android.permission.POST_NOTIFICATIONS");
        Api23.request(this, needed);
    }

    @TargetApi(23)
    private static final class Api23 {
        static void request(MainActivity a, List<String> perms) {
            List<String> missing = new ArrayList<String>();
            for (String p : perms) if (a.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
            if (!missing.isEmpty()) a.requestPermissions(missing.toArray(new String[missing.size()]), 1);
        }
    }
}
