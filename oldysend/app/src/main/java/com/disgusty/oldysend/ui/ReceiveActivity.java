package com.disgusty.oldysend.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.proto.ReceiveController;
import com.disgusty.oldysend.service.Notifs;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.util.Sdk;
import com.disgusty.oldysend.util.Text;

import java.util.HashMap;
import java.util.Map;

/** Incoming request: accept / decline files, or show a received message (LocalSend's ReceivePage). */
public final class ReceiveActivity extends BaseActivity {
    /** Options page selections: fileId → name; null = all files with original names. */
    static Map<String, String> selectedNames;

    private ReceiveController.Session session;
    private TextView status;
    private LinearLayout buttons;
    private boolean closing;

    @Override
    protected void build() {
        session = core.receive.session();
        selectedNames = null;
        Notifs.cancelRequest(this);
        screen.appBar.title(s("receiveTab.title"));
        if (session == null) {
            finish();
            return;
        }
        LinearLayout col = k.vbox();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(k.pagePadding(), k.dp(32), k.pagePadding(), k.dp(24));
        col.addView(k.image(k.icon(UiUtil.deviceIcon(session.sender.deviceType), k.iconColor(), 64)));
        col.addView(k.space(8));
        TextView name = k.text(Kit.T_HEADLINE, session.sender.alias);
        name.setGravity(Gravity.CENTER);
        col.addView(name, Kit.matchWrap());
        LinearLayout chips = k.hbox();
        chips.setGravity(Gravity.CENTER);
        chips.setPadding(0, k.dp(6), 0, k.dp(6));
        chips.addView(k.chip(UiUtil.visualId(session.senderIp)));
        if (session.sender.deviceModel != null) {
            chips.addView(k.space(6));
            chips.addView(k.chip(session.sender.deviceModel));
        }
        col.addView(chips, Kit.matchWrap());
        if (!Text.isEmpty(session.sender.fingerprint)) {
            col.addView(k.button(s("verifyPage.title"), Kit.B_TEXT, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    VerifyActivity.open(ReceiveActivity.this, session.sender.fingerprint);
                }
            }));
        }
        col.addView(k.space(16));
        if (session.message != null) {
            boolean link = Text.isHttpUrl(session.message);
            TextView sub = k.text(Kit.T_SUBTITLE, s(link ? "receivePage.subTitleLink" : "receivePage.subTitleMessage"));
            sub.setGravity(Gravity.CENTER);
            col.addView(sub, Kit.matchWrap());
            col.addView(k.space(12));
            android.view.ViewGroup card = k.card(false);
            card.setPadding(k.dp(16), k.dp(12), k.dp(16), k.dp(12));
            TextView msg = k.text(Kit.T_BODY, session.message);
            if (Sdk.atLeast(11)) Api11.selectable(msg);
            card.addView(msg, Kit.matchWrap());
            col.addView(card, Kit.matchWrap());
        } else {
            TextView sub = k.text(Kit.T_SUBTITLE, t.plural("receivePage.subTitle", session.files.size()));
            sub.setGravity(Gravity.CENTER);
            col.addView(sub, Kit.matchWrap());
            col.addView(k.space(8));
            int shown = 0;
            long total = 0;
            for (ReceiveController.ReceivingFile f : session.files.values()) total += f.dto.size;
            for (ReceiveController.ReceivingFile f : session.files.values()) {
                if (shown++ >= 5) break;
                col.addView(k.row().icon(UiUtil.fileIcon(f.dto.fileType, f.dto.fileName)).title(f.dto.fileName)
                        .summary(Text.fileSize(f.dto.size)).build(), Kit.matchWrap());
            }
            if (session.files.size() > 5) col.addView(k.text(Kit.T_SECONDARY, "… +" + (session.files.size() - 5)));
            col.addView(k.text(Kit.T_SECONDARY, s("sendTab.selection.size", "size", Text.fileSize(total))));
        }
        status = k.text(Kit.T_BODY, "");
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, k.dp(16), 0, 0);
        col.addView(status, Kit.matchWrap());
        screen.setContent(k.scroll(col));

        buttons = k.hbox();
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(k.pagePadding(), k.dp(12), k.pagePadding(), k.dp(16));
        screen.addBottom(buttons);
        buildButtons();
    }

    private void buildButtons() {
        buttons.removeAllViews();
        if (session.message != null) {
            buttons.addView(k.button(s("general.copy"), Kit.B_TEXT, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copy(ReceiveActivity.this, session.message);
                    k.toast(s("general.copiedToClipboard"));
                }
            }));
            if (Text.isHttpUrl(session.message)) {
                buttons.addView(k.button(s("general.open"), Kit.B_TEXT, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(session.message.trim())));
                        } catch (Exception e) {
                            k.toast(s("general.error"));
                        }
                    }
                }));
            }
            buttons.addView(k.space(8));
            buttons.addView(k.button(s("general.close"), Kit.B_PRIMARY, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    core.receive.decide(session, true, null);
                    closing = true;
                    finish();
                }
            }));
            return;
        }
        buttons.addView(k.button(s("receiveOptionsPage.title"), Kit.B_TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(ReceiveOptionsActivity.class);
            }
        }));
        View fill = new View(this);
        buttons.addView(fill, new LinearLayout.LayoutParams(0, 1, 1));
        buttons.addView(k.button(s("general.decline"), Kit.B_SECONDARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closing = true;
                core.receive.decide(session, false, null);
                finish();
            }
        }));
        buttons.addView(k.space(8));
        buttons.addView(k.button(s("general.accept"), Kit.B_PRIMARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closing = true;
                Map<String, String> names = selectedNames;
                if (names == null) {
                    names = new HashMap<String, String>();
                    for (ReceiveController.ReceivingFile f : session.files.values()) names.put(f.dto.id, f.dto.fileName);
                }
                core.receive.decide(session, true, names);
                startActivity(new Intent(ReceiveActivity.this, ProgressActivity.class).putExtra(ProgressActivity.EXTRA_RECEIVE, true));
                finish();
            }
        }));
    }

    @Override
    protected void refresh(int what) {
        if (session == null || closing) return;
        ReceiveController.Session cur = core.receive.session();
        if (cur != session) {
            finish();
            return;
        }
        if (session.status == ReceiveController.Status.CANCELED_BY_SENDER) {
            status.setText(s("receivePage.canceled"));
            buttons.removeAllViews();
            buttons.addView(k.button(s("general.close"), Kit.B_PRIMARY, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    core.receive.clearFinished();
                    finish();
                }
            }));
        } else if (!session.isActive() && session.message == null) {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (session != null && session.status == ReceiveController.Status.WAITING) {
            closing = true;
            core.receive.decide(session, session.message != null, null);
        }
        super.onBackPressed();
    }

    @SuppressWarnings("deprecation")
    static void copy(Context c, String text) {
        if (Sdk.atLeast(11)) Api11.copy(c, text);
        else ((android.text.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE)).setText(text);
    }

    @TargetApi(11)
    private static final class Api11 {
        static void selectable(TextView t) {
            t.setTextIsSelectable(true);
        }

        static void copy(Context c, String text) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("OldySend", text));
        }
    }
}
