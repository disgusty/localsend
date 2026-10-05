package com.disgusty.oldysend.ui;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.net.NetUtil;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.ui.kit.Segmented;
import com.disgusty.oldysend.ui.kit.Toggle;

import java.util.ArrayList;
import java.util.List;

/** Include/exclude network interfaces (LocalSend's NetworkInterfacesPage). */
public final class NetworkInterfacesActivity extends BaseActivity {
    private LinearLayout col;
    private boolean whitelistMode;

    @Override
    protected void build() {
        screen.appBar.title(s("networkInterfacesPage.title")).up(finisher());
        whitelistMode = !core.settings.networkWhitelist().isEmpty();
        col = k.vbox();
        screen.setContent(k.scroll(col));
        rebuildList();
    }

    private void rebuildList() {
        col.removeAllViews();
        TextView info = k.text(Kit.T_SECONDARY, s("networkInterfacesPage.info").replace("LocalSend", "OldySend"));
        info.setPadding(k.pagePadding(), k.dp(16), k.pagePadding(), k.dp(12));
        col.addView(info, Kit.matchWrap());
        Segmented seg = k.segmented(new String[]{s("networkInterfacesPage.blacklist"), s("networkInterfacesPage.whitelist")}, null);
        seg.select(whitelistMode ? 1 : 0);
        seg.listener(new Segmented.Listener() {
            @Override
            public void onSelected(int index) {
                whitelistMode = index == 1;
                core.settings.setNetworkBlacklist(new ArrayList<String>());
                core.settings.setNetworkWhitelist(new ArrayList<String>());
                rebuildList();
            }
        });
        LinearLayout.LayoutParams sp = Kit.matchWrap();
        sp.setMargins(k.pagePadding(), 0, k.pagePadding(), k.dp(8));
        col.addView(seg.view(), sp);
        final List<String> list = whitelistMode ? core.settings.networkWhitelist() : core.settings.networkBlacklist();
        for (final String name : NetUtil.interfaceNames()) {
            final Toggle tg = k.toggle(Kit.CHECKBOX).passive();
            tg.setChecked(list.contains(name));
            col.addView(k.row().icon(Ic.ROUTER).title(name).trailing(tg).onClick(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tg.toggle();
                    if (tg.isChecked()) list.add(name);
                    else list.remove(name);
                    if (whitelistMode) core.settings.setNetworkWhitelist(list);
                    else core.settings.setNetworkBlacklist(list);
                    preview();
                }
            }).build(), Kit.matchWrap());
        }
        col.addView(k.sectionHeader(s("networkInterfacesPage.preview")));
        TextView preview = k.text(Kit.T_BODY, "");
        preview.setTag("preview");
        preview.setPadding(k.pagePadding(), 0, k.pagePadding(), k.dp(16));
        col.addView(preview, Kit.matchWrap());
        preview();
    }

    private void preview() {
        TextView p = (TextView) col.findViewWithTag("preview");
        if (p == null) return;
        StringBuilder sb = new StringBuilder();
        for (NetUtil.LocalAddress a : core.localAddresses()) sb.append(sb.length() > 0 ? "\n" : "").append(a.ip).append(" (").append(a.interfaceName).append(')');
        p.setText(sb.length() == 0 ? "-" : sb.toString());
    }

    @Override
    public void finish() {
        core.restartServer();
        super.finish();
    }
}
