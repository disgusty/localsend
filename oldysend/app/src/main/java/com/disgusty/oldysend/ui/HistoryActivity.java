package com.disgusty.oldysend.ui;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.disgusty.oldysend.data.History;
import com.disgusty.oldysend.files.MimeTypes;
import com.disgusty.oldysend.files.Storage;
import com.disgusty.oldysend.i18n.I18n;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.ui.kit.Action;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.util.Sdk;
import com.disgusty.oldysend.util.Text;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Receive history (LocalSend's ReceiveHistoryPage). */
public final class HistoryActivity extends BaseActivity {
    private List<History.Entry> entries = new ArrayList<History.Entry>();
    private BaseAdapter adapter;
    private TextView empty;
    private ListView list;

    @Override
    protected void build() {
        screen.appBar.title(s("receiveHistoryPage.title")).up(finisher());
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action(Ic.FOLDER_OPEN, s("receiveHistoryPage.openFolder"), new Runnable() {
            @Override
            public void run() {
                openFolder();
            }
        }));
        actions.add(new Action(Ic.DELETE, s("receiveHistoryPage.deleteHistory"), new Runnable() {
            @Override
            public void run() {
                k.dialog().title(s("dialogs.historyClearDialog.title")).message(s("dialogs.historyClearDialog.content"))
                        .negative(s("general.cancel"), null).positive(s("general.delete"), new Runnable() {
                            @Override
                            public void run() {
                                core.history.clear();
                                core.changed(Core.CHANGED_HISTORY);
                            }
                        }).show();
            }
        }).always());
        screen.appBar.actions(actions);
        list = new ListView(this);
        k.styleList(list);
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return entries.size();
            }

            @Override
            public Object getItem(int position) {
                return entries.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                History.Entry e = entries.get(position);
                String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(e.timestamp));
                String sub = date + " · " + (e.isMessage ? "" : Text.fileSize(e.fileSize) + " · ") + e.senderAlias;
                return k.row().icon(e.isMessage ? Ic.SUBJECT : UiUtil.fileIcon(e.fileType, e.fileName))
                        .title(e.isMessage ? e.fileName.replace('\n', ' ') : e.fileName).summary(sub).build();
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                History.Entry e = entries.get(position);
                if (e.isMessage) showMessage(e);
                else openFile(HistoryActivity.this, e.location, e.fileType);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                entryMenu(view, entries.get(position));
                return true;
            }
        });
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(list);
        empty = k.text(Kit.T_SECONDARY, s("receiveHistoryPage.empty"));
        empty.setGravity(android.view.Gravity.CENTER);
        frame.addView(empty, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        screen.setContent(frame);
    }

    @Override
    protected void refresh(int what) {
        entries = core.history.all();
        adapter.notifyDataSetChanged();
        empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void entryMenu(View anchor, final History.Entry e) {
        List<Action> acts = new ArrayList<Action>();
        if (!e.isMessage) {
            acts.add(new Action(Ic.OPEN_IN_NEW, s("receiveHistoryPage.entryActions.open"), new Runnable() {
                @Override
                public void run() {
                    openFile(HistoryActivity.this, e.location, e.fileType);
                }
            }));
        } else {
            acts.add(new Action(Ic.CONTENT_COPY, s("general.copy"), new Runnable() {
                @Override
                public void run() {
                    ReceiveActivity.copy(HistoryActivity.this, e.fileName);
                    k.toast(s("general.copiedToClipboard"));
                }
            }));
        }
        acts.add(new Action(Ic.INFO, s("receiveHistoryPage.entryActions.info"), new Runnable() {
            @Override
            public void run() {
                String msg = s("dialogs.fileInfo.fileName") + " " + e.fileName + "\n"
                        + (e.location != null ? s("dialogs.fileInfo.path") + " " + e.location + "\n" : "")
                        + s("dialogs.fileInfo.size") + " " + Text.fileSize(e.fileSize) + "\n"
                        + s("dialogs.fileInfo.sender") + " " + e.senderAlias + "\n"
                        + s("dialogs.fileInfo.time") + " " + DateFormat.getDateTimeInstance().format(new Date(e.timestamp));
                k.dialog().title(s("dialogs.fileInfo.title")).message(msg).positive(s("general.close"), null).show();
            }
        }));
        acts.add(new Action(Ic.DELETE, s("receiveHistoryPage.entryActions.deleteFromHistory"), new Runnable() {
            @Override
            public void run() {
                core.history.remove(e.id);
                core.changed(Core.CHANGED_HISTORY);
            }
        }));
        if (!e.isMessage && e.location != null) {
            acts.add(new Action(Ic.DELETE, s("general.delete"), new Runnable() {
                @Override
                public void run() {
                    Storage.delete(HistoryActivity.this, e.location);
                    core.history.remove(e.id);
                    core.changed(Core.CHANGED_HISTORY);
                }
            }));
        }
        k.menu(anchor, e.fileName, acts);
    }

    private void showMessage(final History.Entry e) {
        k.dialog().title(e.senderAlias).message(e.fileName)
                .neutral(s("general.copy"), new Runnable() {
                    @Override
                    public void run() {
                        ReceiveActivity.copy(HistoryActivity.this, e.fileName);
                        k.toast(s("general.copiedToClipboard"));
                    }
                })
                .positive(s("general.close"), null).show();
    }

    private void openFolder() {
        String dest = core.settings.destination();
        try {
            if (dest != null && dest.startsWith("content://")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(dest)));
            } else if (Sdk.atLeast(29)) {
                startActivity(new Intent("android.intent.action.VIEW_DOWNLOADS"));
            } else {
                startActivity(new Intent(this, FileBrowserActivity.class).putExtra(FileBrowserActivity.EXTRA_MODE, FileBrowserActivity.MODE_VIEW)
                        .putExtra(FileBrowserActivity.EXTRA_START, dest != null ? dest : Storage.defaultDownloadDir().getAbsolutePath()));
            }
        } catch (Exception ex) {
            k.toast(s("general.error"));
        }
    }

    /** Opens a received file with another app (ACTION_VIEW, read grant for content uris). */
    static void openFile(BaseActivity a, String location, String mime) {
        I18n t = I18n.get();
        if (location == null || !Storage.exists(a, location)) {
            a.k.dialog().title(t.text("dialogs.cannotOpenFile.title"))
                    .message(t.text("dialogs.cannotOpenFile.content", "file", location == null ? "?" : location))
                    .positive(t.text("general.close"), null).show();
            return;
        }
        Uri uri = Storage.uriFor(a, location);
        String type = mime == null || mime.length() == 0 ? MimeTypes.fromName(location) : mime;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, type);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            try {
                Intent any = new Intent(Intent.ACTION_VIEW);
                any.setDataAndType(uri, "*/*");
                any.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                a.startActivity(Intent.createChooser(any, t.text("general.open")));
            } catch (Exception e2) {
                a.k.toast(t.text("general.error"));
            }
        }
    }

}
