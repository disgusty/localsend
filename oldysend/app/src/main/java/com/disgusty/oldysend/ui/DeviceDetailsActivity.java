package com.disgusty.oldysend.ui;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;

import com.disgusty.oldysend.proto.Device;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.Kit;

/** Device information (LocalSend's DeviceDetailsPage). */
public final class DeviceDetailsActivity extends BaseActivity {
    private static Device pending;

    static void open(BaseActivity a, Device d) {
        pending = d;
        a.startActivity(new Intent(a, DeviceDetailsActivity.class));
    }

    @Override
    protected void build() {
        final Device d = pending;
        screen.appBar.title(s("deviceDetailsPage.title")).up(finisher());
        if (d == null) {
            finish();
            return;
        }
        LinearLayout col = k.vbox();
        col.addView(k.row().icon(UiUtil.deviceIcon(d.deviceType)).title(s("deviceDetailsPage.info.name")).summary(d.alias).build(), Kit.matchWrap());
        col.addView(k.row().icon(Ic.ROUTER).title(s("deviceDetailsPage.info.address")).summary(d.protocol() + "://" + d.ip + ":" + d.port).build(), Kit.matchWrap());
        col.addView(k.row().icon(Ic.INFO).title(s("deviceDetailsPage.info.version")).summary(s("deviceDetailsPage.info.protocol", "version", d.version)).build(), Kit.matchWrap());
        if (d.deviceModel != null) col.addView(k.row().icon(Ic.DEVICES).title(s("settingsTab.network.deviceModel")).summary(d.deviceModel).build(), Kit.matchWrap());
        col.addView(k.row().icon(Ic.SECURITY).title("Fingerprint").summary(d.fingerprint).summaryLines(4).build(), Kit.matchWrap());
        col.addView(k.sectionHeader(s("deviceDetailsPage.logs.title")));
        col.addView(k.row().icon(Ic.HISTORY).title(s("deviceDetailsPage.logs.discovered", "protocol", d.discoveredVia, "host", d.ip))
                .summary(java.text.DateFormat.getTimeInstance().format(new java.util.Date(d.lastSeen))).build(), Kit.matchWrap());
        final boolean fav = core.favorites.isFavorite(d.fingerprint);
        LinearLayout bar = k.hbox();
        bar.setPadding(k.pagePadding(), k.dp(16), k.pagePadding(), k.dp(16));
        bar.addView(k.button(s("deviceDetailsPage.favorite"), fav ? Kit.B_SECONDARY : Kit.B_PRIMARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FavoritesDialog.edit(DeviceDetailsActivity.this, core.favorites.byFingerprint(d.fingerprint), d);
            }
        }));
        bar.addView(k.space(8));
        bar.addView(k.button(s("deviceDetailsPage.verify"), Kit.B_SECONDARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VerifyActivity.open(DeviceDetailsActivity.this, d.fingerprint);
            }
        }));
        col.addView(bar);
        screen.setContent(k.scroll(col));
    }
}
