package com.disgusty.oldysend.ui;

import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.App;
import com.disgusty.oldysend.files.SendItem;
import com.disgusty.oldysend.proto.WebShare;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.ui.kit.Toggle;

import java.util.ArrayList;
import java.util.List;

/** "Share via link" / "Receive via link": URLs for browsers and incoming requests (LocalSend's WebSharePage). */
public final class WebShareActivity extends BaseActivity {
    static final String EXTRA_RECEIVE = "receive";

    private boolean receiveMode;
    private LinearLayout col;
    private String lastState;

    static void openShare(BaseActivity a) {
        if (App.selection.isEmpty()) {
            a.k.dialog().title(a.s("dialogs.noFiles.title")).message(a.s("dialogs.noFiles.content")).positive(a.s("general.close"), null).show();
            return;
        }
        a.core.web.startDownload(new ArrayList<SendItem>(App.selection));
        a.startActivity(new Intent(a, WebShareActivity.class));
    }

    static void openReceive(BaseActivity a) {
        a.core.web.startUpload();
        a.startActivity(new Intent(a, WebShareActivity.class).putExtra(EXTRA_RECEIVE, true));
    }

    @Override
    protected void build() {
        receiveMode = getIntent().getBooleanExtra(EXTRA_RECEIVE, false);
        screen.appBar.title(s(receiveMode ? "webReceivePage.title" : "webSharePage.title")).up(finisher());
        col = k.vbox();
        col.setPadding(0, k.dp(8), 0, k.dp(24));
        screen.setContent(k.scroll(col));
    }

    @Override
    protected void refresh(int what) {
        WebShare web = core.web;
        StringBuilder state = new StringBuilder();
        state.append(core.serverRunning()).append(web.mode()).append(web.pin()).append(web.autoAccept()).append(core.https());
        for (WebShare.Request r : web.requests()) state.append(r.ip).append(r.status);
        if (state.toString().equals(lastState)) return;
        lastState = state.toString();
        col.removeAllViews();
        if (!core.serverRunning()) {
            LinearLayout box = k.vbox();
            box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            box.setPadding(0, k.dp(32), 0, 0);
            box.addView(k.spinner());
            box.addView(k.text(Kit.T_BODY, core.serverError() != null ? s("webSharePage.error") : s("webSharePage.loading")));
            col.addView(box, Kit.matchWrap());
            return;
        }
        List<String> urls = web.urls();
        TextView head = k.text(Kit.T_BODY, t.plural("webSharePage.openLink", urls.size()));
        head.setPadding(k.pagePadding(), k.dp(8), k.pagePadding(), k.dp(4));
        col.addView(head, Kit.matchWrap());
        for (final String url : urls) {
            View qr = k.iconButton(Ic.QR_CODE, s("dialogs.qr.title"), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showQr(url);
                }
            });
            col.addView(k.row().icon(Ic.LINK).title(url).trailing(qr).onClick(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ReceiveActivity.copy(WebShareActivity.this, url);
                    k.toast(s("general.copiedToClipboard"));
                }
            }).build(), Kit.matchWrap());
        }
        if (core.https()) {
            TextView hint = k.text(Kit.T_CAPTION, s("webSharePage.encryptionHint"));
            hint.setPadding(k.pagePadding(), k.dp(4), k.pagePadding(), k.dp(4));
            col.addView(hint, Kit.matchWrap());
        }

        col.addView(k.sectionHeader(s("general.settings")));
        final Toggle auto = k.toggle(Kit.SWITCH).passive();
        auto.setChecked(web.autoAccept());
        col.addView(k.row().icon(Ic.DONE_ALL).title(s("webSharePage.autoAccept")).trailing(auto).onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                auto.toggle();
                core.web.setAutoAccept(auto.isChecked());
            }
        }).build(), Kit.matchWrap());
        final Toggle pin = k.toggle(Kit.SWITCH).passive();
        pin.setChecked(web.pin() != null);
        col.addView(k.row().icon(Ic.PASSWORD).title(s("webSharePage.requirePin"))
                .summary(web.pin() != null ? s("webSharePage.pinHint", "pin", web.pin()) : null).trailing(pin).onClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (core.web.pin() != null) {
                            core.web.setPin(null);
                            return;
                        }
                        final com.disgusty.oldysend.ui.kit.Field f = k.field(null, false);
                        Compat.inputType(f.edit, InputType.TYPE_CLASS_NUMBER);
                        k.dialog().title(s("webSharePage.requirePin")).view(f.view).negative(s("general.cancel"), null)
                                .positive(s("general.confirm"), new Runnable() {
                                    @Override
                                    public void run() {
                                        core.web.setPin(f.text().trim());
                                    }
                                }).show();
                    }
                }).build(), Kit.matchWrap());

        if (!receiveMode) {
            col.addView(k.sectionHeader(s("webSharePage.requests")));
            List<WebShare.Request> requests = web.requests();
            if (requests.isEmpty()) {
                TextView none = k.text(Kit.T_SECONDARY, s("webSharePage.noRequests"));
                none.setPadding(k.pagePadding(), k.dp(8), k.pagePadding(), k.dp(8));
                col.addView(none);
            }
            for (final WebShare.Request r : requests) {
                LinearLayout trailing = k.hbox();
                if (r.status == WebShare.RequestStatus.PENDING) {
                    trailing.addView(k.iconButton(Ic.CLOSE, s("general.decline"), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            core.web.decide(r, false);
                        }
                    }));
                    trailing.addView(k.iconButton(Ic.CHECK, s("general.accept"), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            core.web.decide(r, true);
                        }
                    }));
                }
                String status = r.status == WebShare.RequestStatus.ACCEPTED ? s("general.accepted")
                        : r.status == WebShare.RequestStatus.DECLINED ? s("general.decline") : s("sendPage.waiting");
                col.addView(k.row().icon(Ic.LANGUAGE).title(r.browserLabel()).summary(r.ip + " · " + status).trailing(trailing).build(), Kit.matchWrap());
            }
        } else {
            TextView info = k.text(Kit.T_CAPTION, s("receiveTab.link") + ": " + s("oldy.webReceiveHint"));
            info.setPadding(k.pagePadding(), k.dp(12), k.pagePadding(), 0);
            col.addView(info, Kit.matchWrap());
        }
    }

    /** LocalSend's QrDialog: the link as a QR code, the link itself and the PIN when one is required. */
    private void showQr(String url) {
        LinearLayout box = k.vbox();
        box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        box.setPadding(k.dp(8), k.dp(8), k.dp(8), 0);
        int side = Math.min(k.dp(220), getResources().getDisplayMetrics().widthPixels - k.dp(112));
        android.widget.ImageView img = k.image(new QrDrawable(url, side));
        box.addView(img, new LinearLayout.LayoutParams(side, side));
        TextView label = k.text(Kit.T_BODY, url);
        label.setGravity(android.view.Gravity.CENTER);
        label.setPadding(0, k.dp(10), 0, 0);
        box.addView(label, Kit.matchWrap());
        String pin = core.web.pin();
        if (pin != null) {
            LinearLayout row = k.hbox();
            row.setGravity(android.view.Gravity.CENTER);
            row.setPadding(0, k.dp(10), 0, 0);
            row.addView(k.image(k.icon(Ic.PASSWORD, k.iconColor(), 20)));
            row.addView(k.space(6));
            row.addView(k.text(Kit.T_BODY, pin));
            box.addView(row, Kit.matchWrap());
        }
        k.dialog().title(s("dialogs.qr.title")).view(box).positive(s("general.close"), null).show();
    }

    @Override
    public void finish() {
        core.web.stop();
        super.finish();
    }
}
