package com.disgusty.oldysend.ui;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.net.NetUtil;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.ui.kit.Action;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.ui.kit.Segmented;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Receive tab: logo, device name, visual id, quick save and the info box. */
final class ReceiveTab {
    private final MainActivity a;
    private final Kit k;
    private View root;
    private TextView alias;
    private TextView ids;
    private Segmented quickSave;
    private LinearLayout infoBox;
    private TextView infoAlias;
    private TextView infoIps;
    private TextView infoPort;
    private boolean infoVisible;
    private RotatingLogo logo;

    ReceiveTab(MainActivity a) {
        this.a = a;
        this.k = a.k;
    }

    View view() {
        if (root != null) return root;
        LinearLayout col = k.vbox();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(k.pagePadding(), k.dp(24), k.pagePadding(), k.dp(24));

        infoBox = k.vbox();
        ViewGroup card = k.card(false);
        card.setPadding(k.dp(16), k.dp(12), k.dp(16), k.dp(12));
        infoAlias = infoLine(card, a.s("receiveTab.infoBox.alias"));
        infoIps = infoLine(card, a.s("receiveTab.infoBox.ip"));
        infoPort = infoLine(card, a.s("receiveTab.infoBox.port"));
        infoBox.addView(card, Kit.matchWrap());
        infoBox.setVisibility(View.GONE);
        col.addView(infoBox, Kit.matchWrap());

        View fill = new View(a.k.context);
        col.addView(fill, new LinearLayout.LayoutParams(1, 0, 1));
        int size = k.dp(180);
        logo = new RotatingLogo(k.logo(size), size, a.core.settings.animations());
        col.addView(logo, new LinearLayout.LayoutParams(size, size));
        col.addView(k.space(16));
        alias = k.text(Kit.T_DISPLAY, "");
        alias.setGravity(Gravity.CENTER);
        col.addView(alias, Kit.matchWrap());
        ids = k.text(Kit.T_TITLE, "");
        ids.setGravity(Gravity.CENTER);
        ids.setTextColor(k.textColor(Kit.T_SECONDARY));
        col.addView(ids, Kit.matchWrap());
        col.addView(k.space(24));

        TextView qsLabel = k.text(Kit.T_SECTION, a.s("general.quickSave"));
        qsLabel.setGravity(Gravity.CENTER);
        col.addView(qsLabel, Kit.matchWrap());
        col.addView(k.space(8));
        quickSave = k.segmented(new String[]{a.s("receiveTab.quickSave.off"), a.s("receiveTab.quickSave.favorites"), a.s("receiveTab.quickSave.on")}, null);
        quickSave.listener(new Segmented.Listener() {
            @Override
            public void onSelected(int index) {
                String v = index == 0 ? Settings.QUICK_SAVE_OFF : index == 1 ? Settings.QUICK_SAVE_FAVORITES : Settings.QUICK_SAVE_ON;
                a.core.settings.setQuickSave(v);
                if (index == 2 && !a.core.settings.noticeShown("quickSave")) {
                    a.core.settings.setNoticeShown("quickSave");
                    k.dialog().title(a.s("dialogs.quickSaveNotice.title")).message(a.s("dialogs.quickSaveNotice.content"))
                            .positive(a.s("general.continueStr"), null).show();
                }
                if (index == 1 && !a.core.settings.noticeShown("quickSaveFavorites")) {
                    a.core.settings.setNoticeShown("quickSaveFavorites");
                    String[] lines = a.t.array("dialogs.quickSaveFromFavoritesNotice.content");
                    StringBuilder sb = new StringBuilder();
                    for (String l : lines) sb.append(sb.length() > 0 ? "\n\n" : "").append(l);
                    k.dialog().title(a.s("dialogs.quickSaveFromFavoritesNotice.title")).message(sb.toString())
                            .positive(a.s("general.continueStr"), null).show();
                }
            }
        });
        LinearLayout.LayoutParams qp = Kit.matchWrap();
        qp.leftMargin = k.dp(8);
        qp.rightMargin = k.dp(8);
        col.addView(quickSave.view(), qp);
        col.addView(k.space(16));
        col.addView(k.button(a.s("receiveTab.link"), Kit.B_SECONDARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebShareActivity.openReceive(a);
            }
        }));
        View fill2 = new View(a.k.context);
        col.addView(fill2, new LinearLayout.LayoutParams(1, 0, 1));
        root = k.scroll(col);
        return root;
    }

    private TextView infoLine(ViewGroup parent, String label) {
        LinearLayout line = k.hbox();
        line.setGravity(Gravity.TOP);
        TextView l = k.text(Kit.T_SECONDARY, label);
        line.addView(l, new LinearLayout.LayoutParams(k.dp(110), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = k.text(Kit.T_BODY, "-");
        line.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        line.setPadding(0, k.dp(2), 0, k.dp(2));
        parent.addView(line);
        return v;
    }

    void actions(List<Action> actions) {
        actions.add(new Action(Ic.HISTORY, a.s("receiveHistoryPage.title"), new Runnable() {
            @Override
            public void run() {
                a.open(HistoryActivity.class);
            }
        }).always());
        actions.add(new Action(Ic.INFO, a.s("receiveTab.infoBox.alias").replace(":", ""), new Runnable() {
            @Override
            public void run() {
                infoVisible = !infoVisible;
                infoBox.setVisibility(infoVisible ? View.VISIBLE : View.GONE);
            }
        }).always());
    }

    void refresh(int what) {
        if (root == null) return;
        Core core = a.core;
        String name = core.alias();
        alias.setText(name);
        boolean running = core.serverRunning();
        List<NetUtil.LocalAddress> addrs = core.localAddresses();
        if (!running) {
            ids.setText(core.serverStarting() ? "…" : a.s("general.offline"));
        } else {
            Set<String> v = new LinkedHashSet<String>();
            for (NetUtil.LocalAddress l : addrs) v.add(UiUtil.visualId(l.ip));
            StringBuilder sb = new StringBuilder();
            for (String s : v) sb.append(sb.length() > 0 ? " " : "").append(s);
            ids.setText(sb.length() == 0 ? a.s("general.offline") : sb.toString());
        }
        logo.setSpinning(running);
        String qs = core.settings.quickSave();
        quickSave.select(Settings.QUICK_SAVE_ON.equals(qs) ? 2 : Settings.QUICK_SAVE_FAVORITES.equals(qs) ? 1 : 0);
        infoAlias.setText(name);
        StringBuilder ips = new StringBuilder();
        for (NetUtil.LocalAddress l : addrs) ips.append(ips.length() > 0 ? "\n" : "").append(l.ip);
        infoIps.setText(ips.length() == 0 ? a.s("general.unknown") : ips.toString());
        infoPort.setText(running ? String.valueOf(core.port()) : "-");
    }

    /** LocalSend's slowly rotating logo while the server runs. */
    private static final class RotatingLogo extends View {
        private final Drawable d;
        private final int size;
        private final boolean animate;
        private boolean spinning;
        private final long start = SystemClock.uptimeMillis();

        RotatingLogo(Drawable d, int size, boolean animate) {
            super(com.disgusty.oldysend.App.get());
            this.d = d;
            this.size = size;
            this.animate = animate;
        }

        void setSpinning(boolean s) {
            spinning = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            d.setBounds(0, 0, size, size);
            float angle = 0;
            if (spinning && animate) {
                angle = ((SystemClock.uptimeMillis() - start) % 15000) / 15000f * 360f;
                postInvalidateDelayed(40);
            }
            if (d instanceof com.disgusty.oldysend.ui.kit.LogoDrawable) {
                ((com.disgusty.oldysend.ui.kit.LogoDrawable) d).setAngle(angle);
                d.draw(canvas);
            } else {
                int save = canvas.save();
                canvas.rotate(angle, size / 2f, size / 2f);
                d.draw(canvas);
                canvas.restoreToCount(save);
            }
        }
    }
}
