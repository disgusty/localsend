package com.disgusty.oldysend.ui;

import android.view.View;
import android.widget.LinearLayout;

import com.disgusty.oldysend.files.Storage;
import com.disgusty.oldysend.proto.ReceiveController;
import com.disgusty.oldysend.ui.kit.Field;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.ui.kit.Toggle;
import com.disgusty.oldysend.util.Text;

import java.util.LinkedHashMap;
import java.util.Map;

/** Destination, gallery and per-file selection / renaming before accepting (LocalSend's ReceiveOptionsPage). */
public final class ReceiveOptionsActivity extends BaseActivity {
    private ReceiveController.Session session;
    private LinearLayout col;
    private final Map<String, String> names = new LinkedHashMap<String, String>();

    @Override
    protected void build() {
        session = core.receive.session();
        screen.appBar.title(s("receiveOptionsPage.title")).up(finisher());
        if (session == null) {
            finish();
            return;
        }
        if (ReceiveActivity.selectedNames != null) names.putAll(ReceiveActivity.selectedNames);
        else for (ReceiveController.ReceivingFile f : session.files.values()) names.put(f.dto.id, f.dto.fileName);
        col = k.vbox();
        screen.setContent(k.scroll(col));
        rebuildList();
    }

    private void rebuildList() {
        col.removeAllViews();
        col.addView(k.row().icon(com.disgusty.oldysend.ui.kit.Ic.FOLDER).title(s("receiveOptionsPage.destination"))
                .summary(session.destination == null ? s("settingsTab.receive.downloads") : Storage.describe(this, session.destination)).build());
        final Toggle gallery = k.toggle(Kit.SWITCH).passive();
        gallery.setChecked(session.saveToGallery);
        boolean folders = session.hasFolders();
        col.addView(k.row().icon(com.disgusty.oldysend.ui.kit.Ic.PHOTO_LIBRARY).title(s("receiveOptionsPage.saveToGallery"))
                .summary(folders ? s("receiveOptionsPage.saveToGalleryOff") : null).trailing(gallery).enabled(!folders)
                .onClick(folders ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        gallery.toggle();
                        session.saveToGallery = gallery.isChecked();
                    }
                }).build());
        col.addView(k.sectionHeader(s("general.files")));
        for (final ReceiveController.ReceivingFile f : session.files.values()) {
            final Toggle check = k.toggle(Kit.CHECKBOX).passive();
            final boolean selected = names.containsKey(f.dto.id);
            check.setChecked(selected);
            String name = selected ? names.get(f.dto.id) : f.dto.fileName;
            String summary = Text.fileSize(f.dto.size);
            if (selected && !name.equals(f.dto.fileName)) summary += " · " + s("dialogs.fileNameInput.original", "original", f.dto.fileName);
            col.addView(k.row().icon(UiUtil.fileIcon(f.dto.fileType, f.dto.fileName)).title(name).summary(summary).trailing(check)
                    .onClick(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (names.containsKey(f.dto.id)) names.remove(f.dto.id);
                            else names.put(f.dto.id, f.dto.fileName);
                            save();
                            rebuildList();
                        }
                    })
                    .onLongClick(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            rename(f);
                            return true;
                        }
                    }).build(), Kit.matchWrap());
        }
        LinearLayout hint = k.vbox();
        hint.setPadding(k.pagePadding(), k.dp(8), k.pagePadding(), k.dp(16));
        hint.addView(k.text(Kit.T_CAPTION, s("dialogs.fileNameInput.title") + ": " + s("oldy.longPressToRename")));
        col.addView(hint);
    }

    private void rename(final ReceiveController.ReceivingFile f) {
        final Field field = k.field(null, false);
        field.text(names.containsKey(f.dto.id) ? names.get(f.dto.id) : f.dto.fileName);
        LinearLayout box = k.vbox();
        box.addView(k.text(Kit.T_CAPTION, s("dialogs.fileNameInput.original", "original", f.dto.fileName)));
        box.addView(field.view, Kit.matchWrap());
        final com.disgusty.oldysend.ui.kit.DialogBuilder d = k.dialog().title(s("dialogs.fileNameInput.title")).view(box).keepOpen();
        d.negative(s("general.cancel"), new Runnable() {
            @Override
            public void run() {
                d.dismiss();
            }
        }).positive(s("general.confirm"), new Runnable() {
            @Override
            public void run() {
                String n = field.text().trim();
                if (n.length() == 0) {
                    field.setError(s("sanitization.empty"));
                    return;
                }
                String leaf = n.substring(n.lastIndexOf('/') + 1);
                if (!Storage.isValidFileName(leaf)) {
                    field.setError(s("sanitization.invalid"));
                    return;
                }
                names.put(f.dto.id, n);
                save();
                d.dismiss();
                rebuildList();
            }
        }).show();
    }

    private void save() {
        ReceiveActivity.selectedNames = new LinkedHashMap<String, String>(names);
    }

    @Override
    protected void refresh(int what) {
        if (session != null && core.receive.session() != session) finish();
    }
}
