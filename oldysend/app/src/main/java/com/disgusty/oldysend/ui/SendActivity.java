package com.disgusty.oldysend.ui;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.App;
import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.i18n.I18n;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.proto.SendController;
import com.disgusty.oldysend.ui.kit.Kit;

/** "Waiting for response…" page of a single-recipient transfer (LocalSend's SendPage). */
public final class SendActivity extends BaseActivity {
    static final String EXTRA_SESSION = "session";

    private SendController.Session session;
    private TextView status;
    private View spinner;
    private LinearLayout buttons;
    private boolean movedOn;

    @Override
    protected void build() {
        session = core.send.byId(getIntent().getStringExtra(EXTRA_SESSION));
        screen.appBar.title(s("sendTab.title")).up(finisher());
        if (session == null) {
            finish();
            return;
        }
        LinearLayout col = k.vbox();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(k.pagePadding(), k.dp(32), k.pagePadding(), k.dp(24));
        col.addView(k.image(k.icon(UiUtil.deviceIcon(core.settings.deviceType()), k.iconColor(), 56)));
        col.addView(k.text(Kit.T_TITLE, core.alias()));
        col.addView(k.space(12));
        col.addView(k.image(k.icon(com.disgusty.oldysend.ui.kit.Ic.EXPAND_MORE, k.iconColor(), 32)));
        col.addView(k.space(12));
        col.addView(k.image(k.icon(UiUtil.deviceIcon(session.target.deviceType), k.iconColor(), 56)));
        TextView target = k.text(Kit.T_TITLE, session.target.alias);
        target.setGravity(Gravity.CENTER);
        col.addView(target);
        if (session.target.deviceModel != null) col.addView(k.chip(session.target.deviceModel));
        if (session.target.fingerprint != null && session.target.fingerprint.length() > 0) {
            col.addView(k.button(s("verifyPage.title"), Kit.B_TEXT, new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    VerifyActivity.open(SendActivity.this, session.target.fingerprint);
                }
            }));
        }
        col.addView(k.space(32));
        spinner = k.spinner();
        col.addView(spinner);
        col.addView(k.space(16));
        status = k.text(Kit.T_BODY, "");
        status.setGravity(Gravity.CENTER);
        col.addView(status, Kit.matchWrap());
        col.addView(k.space(24));
        buttons = k.hbox();
        buttons.setGravity(Gravity.CENTER);
        col.addView(buttons, Kit.matchWrap());
        screen.setContent(k.scroll(col));
    }

    @Override
    protected void refresh(int what) {
        if (session == null || movedOn) return;
        SendController.Status st = session.status;
        status.setText(statusText(this, session));
        boolean active = session.isActive();
        spinner.setVisibility(active ? View.VISIBLE : View.GONE);
        buttons.removeAllViews();
        if (st == SendController.Status.SENDING) {
            movedOn = true;
            startActivity(new Intent(this, ProgressActivity.class).putExtra(ProgressActivity.EXTRA_SEND, session.id));
            finish();
            return;
        }
        if (st == SendController.Status.FINISHED) {
            // A message (204): done without a progress page.
            movedOn = true;
            if (Settings.SEND_SINGLE.equals(core.settings.sendMode())) App.selection.clear();
            core.send.remove(session);
            core.changed(Core.CHANGED_SEND);
            k.toast(s("general.finished"));
            finish();
            return;
        }
        if (active) {
            buttons.addView(k.button(s("general.cancel"), Kit.B_SECONDARY, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    core.send.cancel(session);
                }
            }));
        } else {
            buttons.addView(k.button(s("general.close"), Kit.B_PRIMARY, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    core.send.remove(session);
                    finish();
                }
            }));
        }
    }

    @Override
    public void onBackPressed() {
        if (session != null && session.isActive()) core.send.cancel(session);
        else if (session != null) core.send.remove(session);
        super.onBackPressed();
    }

    /** Human text for a send session state (also used in "multiple recipients" device rows). */
    static String statusText(Context c, SendController.Session s) {
        I18n t = I18n.get();
        switch (s.status) {
            case PREPARING:
                return s.checksumProgress > 0 ? t.text("sendPage.calculatingChecksum", "curr", s.checksumProgress, "n", s.files.size()) : t.text("sendPage.waiting");
            case WAITING:
                return t.text("sendPage.waiting");
            case SENDING:
                long total = s.totalBytes();
                int pct = total == 0 ? 0 : (int) (s.sentBytes() * 100 / total);
                return t.text("progressPage.titleSending") + " " + pct + "%";
            case FINISHED:
                return t.text("general.finished");
            case FINISHED_WITH_ERRORS:
                return t.text("progressPage.total.title.finishedError");
            case DECLINED:
                return t.text("sendPage.rejected");
            case RECIPIENT_BUSY:
                return t.text("sendPage.busy");
            case TOO_MANY_ATTEMPTS:
                return t.text("sendPage.tooManyAttempts");
            case CANCELED_BY_SENDER:
                return t.text("progressPage.total.title.canceledSender");
            case CANCELED_BY_RECEIVER:
                return t.text("progressPage.total.title.canceledReceiver");
            default:
                return t.text("general.error") + (s.errorMessage != null ? ": " + s.errorMessage : "");
        }
    }

    static FrameLayout.LayoutParams center() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    }
}
