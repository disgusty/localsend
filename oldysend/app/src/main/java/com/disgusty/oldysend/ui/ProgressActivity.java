package com.disgusty.oldysend.ui;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.App;
import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.files.Storage;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.proto.ReceiveController;
import com.disgusty.oldysend.proto.SendController;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.ui.kit.Progress;
import com.disgusty.oldysend.util.Text;

import java.util.ArrayList;
import java.util.List;

/** File list with per-file and total progress, speed and remaining time (LocalSend's ProgressPage). */
public final class ProgressActivity extends BaseActivity {
    public static final String EXTRA_RECEIVE = "receive";
    public static final String EXTRA_SEND = "send";

    private boolean receiving;
    private SendController.Session sendSession;
    private final List<FileRow> rows = new ArrayList<FileRow>();
    private TextView totalTitle;
    private TextView totalCount;
    private TextView totalSize;
    private TextView totalSpeed;
    private Progress totalBar;
    private LinearLayout buttons;
    private String buttonsState;
    private long lastBytes;
    private long lastTime;
    private double speed;

    private static final class FileRow {
        String id;
        TextView status;
        Progress bar;
        ImageView icon;
        View view;
    }

    @Override
    protected void build() {
        receiving = getIntent().getBooleanExtra(EXTRA_RECEIVE, false);
        if (!receiving) sendSession = core.send.byId(getIntent().getStringExtra(EXTRA_SEND));
        screen.appBar.title(s(receiving ? "progressPage.titleReceiving" : "progressPage.titleSending"));
        LinearLayout col = k.vbox();
        col.setPadding(0, k.dp(8), 0, k.dp(8));
        if (receiving) {
            ReceiveController.Session s = core.receive.session();
            if (s == null) {
                finish();
                return;
            }
            for (ReceiveController.ReceivingFile f : s.files.values()) {
                if (f.status == ReceiveController.FileStatus.SKIPPED) continue;
                col.addView(fileRow(f.dto.id, f.desiredName, f.dto.fileType, f.dto.size), Kit.matchWrap());
            }
        } else {
            if (sendSession == null) {
                finish();
                return;
            }
            for (SendController.SendingFile f : sendSession.files.values()) {
                if (f.status == SendController.FileStatus.SKIPPED) continue;
                col.addView(fileRow(f.item.id, f.item.name, f.item.mime, f.item.size), Kit.matchWrap());
            }
        }
        screen.setContent(k.scroll(col));

        LinearLayout bottom = k.vbox();
        bottom.setPadding(k.pagePadding(), k.dp(12), k.pagePadding(), k.dp(12));
        bottom.setBackgroundColor(k.c.surfaceContainer);
        totalTitle = k.text(Kit.T_SUBTITLE, "");
        bottom.addView(totalTitle);
        totalBar = k.progress(false);
        LinearLayout.LayoutParams bp = Kit.matchWrap();
        bp.topMargin = k.dp(8);
        bp.bottomMargin = k.dp(8);
        bottom.addView(totalBar.view(), bp);
        LinearLayout stats = k.hbox();
        totalCount = k.text(Kit.T_SECONDARY, "");
        totalSize = k.text(Kit.T_SECONDARY, "");
        totalSpeed = k.text(Kit.T_SECONDARY, "");
        LinearLayout statCol = k.vbox();
        statCol.addView(totalCount);
        statCol.addView(totalSize);
        statCol.addView(totalSpeed);
        stats.addView(statCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        buttons = k.hbox();
        stats.addView(buttons);
        bottom.addView(stats, Kit.matchWrap());
        screen.addBottom(bottom);
    }

    private View fileRow(String id, String name, String mime, long size) {
        FileRow r = new FileRow();
        r.id = id;
        LinearLayout row = k.hbox();
        row.setPadding(k.dp(16), k.dp(8), k.dp(16), k.dp(8));
        r.icon = k.image(k.icon(UiUtil.fileIcon(mime, name), k.iconColor(), 32));
        row.addView(r.icon, new LinearLayout.LayoutParams(k.dp(40), k.dp(40)));
        LinearLayout texts = k.vbox();
        texts.setPadding(k.dp(16), 0, 0, 0);
        TextView title = k.text(Kit.T_SUBTITLE, name);
        k.ellipsize(title, 1);
        texts.addView(title);
        r.bar = k.progress(false);
        LinearLayout.LayoutParams bp = Kit.matchWrap();
        bp.topMargin = k.dp(4);
        bp.bottomMargin = k.dp(2);
        texts.addView(r.bar.view(), bp);
        r.status = k.text(Kit.T_CAPTION, Text.fileSize(size));
        texts.addView(r.status);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.view = row;
        rows.add(r);
        return row;
    }

    @Override
    protected void refresh(int what) {
        if (receiving) refreshReceive();
        else refreshSend();
    }

    private void refreshReceive() {
        final ReceiveController.Session s = core.receive.session();
        if (s == null) {
            finish();
            return;
        }
        for (FileRow r : rows) {
            ReceiveController.ReceivingFile f = s.files.get(r.id);
            if (f == null) continue;
            fileState(r, f.status.name(), f.dto.size == 0 ? 1 : f.received / (float) f.dto.size, f.dto.size, f.error);
            if (f.savedToGallery && f.status == ReceiveController.FileStatus.FINISHED) r.status.setText(s("progressPage.savedToGallery"));
        }
        long total = s.totalBytes();
        long done = s.receivedBytes();
        int finished = s.count(ReceiveController.FileStatus.FINISHED);
        totals(s.status == ReceiveController.Status.SENDING, total, done, finished, s.acceptedCount(), s.startTime, titleFor(s.status.name()));
        boolean active = s.isActive();
        if (!active && s.status == ReceiveController.Status.FINISHED && core.settings.autoFinish()) {
            core.receive.clearFinished();
            finish();
            return;
        }
        setButtons(active, new Runnable() {
            @Override
            public void run() {
                confirmCancel(new Runnable() {
                    @Override
                    public void run() {
                        core.receive.cancelByReceiver();
                    }
                });
            }
        }, new Runnable() {
            @Override
            public void run() {
                core.receive.clearFinished();
                finish();
            }
        }, !active && s.status == ReceiveController.Status.FINISHED ? s : null);
    }

    private void refreshSend() {
        final SendController.Session s = sendSession;
        for (FileRow r : rows) {
            SendController.SendingFile f = s.files.get(r.id);
            if (f == null) continue;
            fileState(r, f.status.name(), f.item.size == 0 ? 1 : f.sent / (float) f.item.size, f.item.size, f.error);
        }
        int finished = s.count(SendController.FileStatus.FINISHED);
        totals(s.status == SendController.Status.SENDING, s.totalBytes(), s.sentBytes(), finished, s.acceptedCount(), s.startTime,
                s.status == SendController.Status.SENDING ? null : SendActivity.statusText(this, s));
        setButtons(s.isActive(), new Runnable() {
            @Override
            public void run() {
                confirmCancel(new Runnable() {
                    @Override
                    public void run() {
                        core.send.cancel(s);
                    }
                });
            }
        }, new Runnable() {
            @Override
            public void run() {
                if (s.status == SendController.Status.FINISHED && Settings.SEND_SINGLE.equals(core.settings.sendMode())) {
                    App.selection.clear();
                }
                core.send.remove(s);
                core.changed(Core.CHANGED_SEND);
                finish();
            }
        }, null);
    }

    private String titleFor(String status) {
        if ("FINISHED".equals(status)) return s("general.finished");
        if ("FINISHED_WITH_ERRORS".equals(status)) return s("progressPage.total.title.finishedError");
        if ("CANCELED_BY_SENDER".equals(status)) return s("progressPage.total.title.canceledSender");
        if ("CANCELED_BY_RECEIVER".equals(status)) return s("progressPage.total.title.canceledReceiver");
        return null;
    }

    private void fileState(FileRow r, String status, float progress, long size, String error) {
        boolean sending = "SENDING".equals(status);
        r.bar.view().setVisibility(sending || "FINISHED".equals(status) ? View.VISIBLE : View.INVISIBLE);
        r.bar.setProgress("FINISHED".equals(status) ? 1 : progress);
        String text;
        if ("FINISHED".equals(status)) text = s("general.finished");
        else if ("FAILED".equals(status)) text = s("general.error") + (error != null ? ": " + error : "");
        else if ("SKIPPED".equals(status)) text = s("general.skipped");
        else if (sending) text = Text.fileSize((long) (size * progress)) + " / " + Text.fileSize(size);
        else text = s("general.queue");
        r.status.setText(text);
        r.status.setTextColor("FAILED".equals(status) ? k.c.error : k.textColor(Kit.T_CAPTION));
    }

    private void totals(boolean running, long total, long done, int finished, int count, long start, String finalTitle) {
        long now = System.currentTimeMillis();
        if (lastTime == 0) {
            lastTime = now;
            lastBytes = done;
        } else if (now - lastTime >= 1000) {
            double inst = (done - lastBytes) * 1000.0 / (now - lastTime);
            speed = speed == 0 ? inst : speed * 0.6 + inst * 0.4;
            lastTime = now;
            lastBytes = done;
        }
        if (finalTitle != null) {
            totalTitle.setText(finalTitle);
        } else {
            long remainingSec = speed > 0 ? (long) ((total - done) / speed) : 0;
            totalTitle.setText(s("progressPage.total.title.sending", "time", UiUtil.remaining(remainingSec)));
        }
        totalBar.setProgress(total == 0 ? 1 : done / (float) total);
        totalCount.setText(s("progressPage.total.count", "curr", finished, "n", count));
        totalSize.setText(s("progressPage.total.size", "curr", Text.fileSize(done), "n", Text.fileSize(total)));
        totalSpeed.setText(running ? s("progressPage.total.speed", "speed", Text.fileSize((long) speed)) : "");
    }

    private void setButtons(boolean active, final Runnable cancel, final Runnable done, final ReceiveController.Session openable) {
        String state = active + "/" + (openable != null);
        if (state.equals(buttonsState)) return;
        buttonsState = state;
        buttons.removeAllViews();
        if (active) {
            buttons.addView(k.button(s("general.cancel"), Kit.B_SECONDARY, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cancel.run();
                }
            }));
            return;
        }
        if (openable != null && openable.files.size() == 1) {
            final ReceiveController.ReceivingFile f = openable.files.values().iterator().next();
            if (f.location != null) {
                buttons.addView(k.button(s("general.open"), Kit.B_TEXT, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        HistoryActivity.openFile(ProgressActivity.this, f.location, f.dto.fileType);
                    }
                }));
                buttons.addView(k.space(8));
            }
        }
        buttons.addView(k.button(s("general.done"), Kit.B_PRIMARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                done.run();
            }
        }));
    }

    private void confirmCancel(final Runnable r) {
        k.dialog().title(s("dialogs.cancelSession.title")).message(s("dialogs.cancelSession.content"))
                .negative(s("general.cancel"), null).positive(s("general.confirm"), r).show();
    }

    @Override
    public void onBackPressed() {
        boolean active = receiving ? core.receive.session() != null && core.receive.session().isActive()
                : sendSession != null && sendSession.isActive();
        if (active) {
            moveTaskToBack(true);
            return;
        }
        if (receiving) core.receive.clearFinished();
        else if (sendSession != null) core.send.remove(sendSession);
        super.onBackPressed();
    }

}
