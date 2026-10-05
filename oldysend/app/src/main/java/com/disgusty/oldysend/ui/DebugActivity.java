package com.disgusty.oldysend.ui;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

import com.disgusty.oldysend.ui.kit.Action;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.util.Log;

import java.util.ArrayList;
import java.util.List;

/** Log viewer for troubleshooting old devices. */
public final class DebugActivity extends BaseActivity {
    private TextView text;

    @Override
    protected void build() {
        screen.appBar.title(s("oldy.debug")).up(finisher());
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action(Ic.CONTENT_COPY, s("general.copy"), new Runnable() {
            @Override
            public void run() {
                ReceiveActivity.copy(DebugActivity.this, text.getText().toString());
                k.toast(s("general.copiedToClipboard"));
            }
        }).always());
        actions.add(new Action(Ic.REFRESH, s("sendTab.scan"), new Runnable() {
            @Override
            public void run() {
                refresh(-1);
            }
        }).always());
        screen.appBar.actions(actions);
        text = new TextView(this);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        text.setTextColor(k.c.onSurface);
        text.setPadding(k.dp(8), k.dp(8), k.dp(8), k.dp(8));
        screen.setContent(k.scroll(text));
    }

    @Override
    protected void refresh(int what) {
        if (what != -1) return;
        StringBuilder sb = new StringBuilder();
        sb.append("fingerprint: ").append(core.fingerprint()).append('\n');
        sb.append("server: ").append(core.serverRunning() ? (core.https() ? "https" : "http") + ":" + core.port() : "off").append('\n');
        sb.append("style: ").append(k.style).append('\n').append('\n');
        for (String l : Log.snapshot()) sb.append(l).append('\n');
        text.setText(sb.toString());
    }
}
