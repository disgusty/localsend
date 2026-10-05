package com.disgusty.oldysend.ui;

import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.ui.kit.Kit;

/** Common problems and their solutions (LocalSend's TroubleshootPage). */
public final class TroubleshootActivity extends BaseActivity {
    @Override
    protected void build() {
        screen.appBar.title(s("troubleshootPage.title")).up(finisher());
        LinearLayout col = k.vbox();
        col.setPadding(k.pagePadding(), k.dp(16), k.pagePadding(), k.dp(24));
        col.addView(k.text(Kit.T_BODY, s("troubleshootPage.subTitle")), Kit.matchWrap());
        item(col, s("troubleshootPage.firewall.symptom"), s("troubleshootPage.firewall.solution", "port", core.port()));
        item(col, s("troubleshootPage.noDiscovery.symptom"), s("troubleshootPage.noDiscovery.solution"));
        item(col, s("troubleshootPage.noConnection.symptom"), s("troubleshootPage.noConnection.solution"));
        item(col, s("oldy.oldAndroidSymptom"), s("oldy.oldAndroidSolution"));
        screen.setContent(k.scroll(col));
    }

    private void item(LinearLayout col, String symptom, String solution) {
        android.view.ViewGroup card = k.card(false);
        card.setPadding(k.dp(16), k.dp(12), k.dp(16), k.dp(12));
        TextView s1 = k.text(Kit.T_SUBTITLE, symptom);
        card.addView(s1);
        TextView label = k.text(Kit.T_SECTION, s("troubleshootPage.solution"));
        label.setPadding(0, k.dp(8), 0, k.dp(2));
        card.addView(label);
        card.addView(k.text(Kit.T_SECONDARY, solution));
        LinearLayout.LayoutParams p = Kit.matchWrap();
        p.topMargin = k.dp(16);
        col.addView(card, p);
    }
}
