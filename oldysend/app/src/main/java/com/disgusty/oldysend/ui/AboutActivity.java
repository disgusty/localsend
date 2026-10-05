package com.disgusty.oldysend.ui;

import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.BuildConfig;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.util.Sdk;

/** About OldySend / LocalSend. */
public final class AboutActivity extends BaseActivity {
    @Override
    protected void build() {
        screen.appBar.title(s("aboutPage.title").replace("LocalSend", "OldySend")).up(finisher());
        LinearLayout col = k.vbox();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(k.pagePadding(), k.dp(24), k.pagePadding(), k.dp(24));
        int size = k.dp(96);
        col.addView(k.image(k.logo(size)), new LinearLayout.LayoutParams(size, size));
        col.addView(k.space(8));
        col.addView(k.text(Kit.T_HEADLINE, "OldySend"));
        col.addView(k.text(Kit.T_SECONDARY, BuildConfig.VERSION_NAME + " · Android " + Sdk.release() + " (API " + Sdk.INT + ")"));
        col.addView(k.space(16));
        TextView fork = k.text(Kit.T_BODY, s("oldy.aboutFork"));
        fork.setGravity(Gravity.CENTER);
        col.addView(fork, Kit.matchWrap());
        col.addView(k.space(12));
        StringBuilder sb = new StringBuilder();
        for (String p : t.array("aboutPage.description")) sb.append(sb.length() > 0 ? "\n\n" : "").append(p);
        TextView desc = k.text(Kit.T_SECONDARY, sb.toString());
        desc.setGravity(Gravity.CENTER);
        col.addView(desc, Kit.matchWrap());
        col.addView(k.space(16));
        LinearLayout links = k.vbox();
        links.addView(k.row().icon(Ic.OPEN_IN_NEW).title("LocalSend").summary("https://localsend.org").onClick(link("https://localsend.org")).build(), Kit.matchWrap());
        links.addView(k.row().icon(Ic.CODE).title("Source (LocalSend)").summary("https://github.com/localsend/localsend")
                .onClick(link("https://github.com/localsend/localsend")).build(), Kit.matchWrap());
        links.addView(k.row().icon(Ic.DESCRIPTION).title(s("oldy.licenses")).summary(s("oldy.licensesText") + " Noto Sans Syriac: SIL Open Font License 1.1.").summaryLines(10).build(), Kit.matchWrap());
        col.addView(links, Kit.matchWrap());
        screen.setContent(k.scroll(col));
    }

    private View.OnClickListener link(final String url) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    k.toast(url);
                }
            }
        };
    }
}
