package com.disgusty.oldysend.ui;

import android.view.View;
import android.widget.LinearLayout;

import com.disgusty.oldysend.App;
import com.disgusty.oldysend.files.SendItem;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.ui.kit.Action;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.util.Text;

import java.util.ArrayList;
import java.util.List;

/** The current send selection with remove buttons (LocalSend's SelectedFilesPage). */
public final class SelectedFilesActivity extends BaseActivity {
    private LinearLayout col;

    @Override
    protected void build() {
        screen.appBar.title(s("sendTab.selection.title")).up(finisher());
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action(Ic.DELETE, s("selectedFilesPage.deleteAll"), new Runnable() {
            @Override
            public void run() {
                App.selection.clear();
                core.changed(Core.CHANGED_SEND);
                finish();
            }
        }).always());
        screen.appBar.actions(actions);
        col = k.vbox();
        screen.setContent(k.scroll(col));
    }

    @Override
    protected void refresh(int what) {
        col.removeAllViews();
        long size = 0;
        for (final SendItem item : new ArrayList<SendItem>(App.selection)) {
            size += item.size;
            String title = item.isText() ? item.text.replace('\n', ' ') : item.name;
            View remove = k.iconButton(Ic.CLOSE, s("general.delete"), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    App.selection.remove(item);
                    core.changed(Core.CHANGED_SEND);
                    if (App.selection.isEmpty()) finish();
                }
            });
            col.addView(k.row().icon(UiUtil.fileIcon(item.mime, item.name)).title(title).summary(Text.fileSize(item.size)).trailing(remove).build(),
                    Kit.matchWrap());
        }
        screen.appBar.subtitle(s("sendTab.selection.files", "files", App.selection.size()) + " · " + Text.fileSize(size));
    }
}
