package com.disgusty.oldysend.ui;

import android.content.Intent;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.disgusty.oldysend.files.MimeTypes;
import com.disgusty.oldysend.ui.kit.Action;
import com.disgusty.oldysend.ui.kit.Field;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.ui.kit.Toggle;
import com.disgusty.oldysend.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Built-in file browser for Android versions without the Storage Access Framework (before 4.4/5.0):
 * pick files, pick a folder (send a folder / receive destination) or just browse.
 */
public final class FileBrowserActivity extends BaseActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_START = "start";
    public static final String RESULT_PATHS = "paths";
    public static final String RESULT_FOLDER = "folder";
    public static final int MODE_FILES = 0;
    public static final int MODE_FOLDER = 1;
    public static final int MODE_VIEW = 2;

    private int mode;
    private File dir;
    private final List<File> entries = new ArrayList<File>();
    private final Set<String> selected = new HashSet<String>();
    private BaseAdapter adapter;
    private View confirm;
    private LinearLayout bar;

    @Override
    protected void build() {
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_FILES);
        String start = getIntent().getStringExtra(EXTRA_START);
        dir = start != null ? new File(start) : Environment.getExternalStorageDirectory();
        if (!dir.isDirectory()) dir = Environment.getExternalStorageDirectory();
        screen.appBar.title(s(mode == MODE_FOLDER ? "oldy.chooseFolder" : mode == MODE_FILES ? "sendTab.picker.file" : "receiveHistoryPage.openFolder")).up(finisher());
        if (mode == MODE_FOLDER) {
            List<Action> actions = new ArrayList<Action>();
            actions.add(new Action(Ic.CREATE_NEW_FOLDER, s("oldy.newFolder"), new Runnable() {
                @Override
                public void run() {
                    newFolder();
                }
            }).always());
            screen.appBar.actions(actions);
        }
        ListView list = new ListView(this);
        k.styleList(list);
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return entries.size() + (dir.getParentFile() != null ? 1 : 0);
            }

            @Override
            public Object getItem(int position) {
                return position;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                boolean hasUp = dir.getParentFile() != null;
                if (hasUp && position == 0) return k.row().icon(Ic.ARROW_BACK).title("..").summary(dir.getParent()).build();
                File f = entries.get(position - (hasUp ? 1 : 0));
                if (f.isDirectory()) return k.row().icon(Ic.FOLDER).title(f.getName()).build();
                Toggle tg = null;
                if (mode == MODE_FILES) {
                    tg = k.toggle(Kit.CHECKBOX).passive();
                    tg.setChecked(selected.contains(f.getAbsolutePath()));
                }
                return k.row().icon(UiUtil.fileIcon(MimeTypes.fromName(f.getName()), f.getName())).title(f.getName())
                        .summary(Text.fileSize(f.length())).trailing(tg).enabled(mode != MODE_FOLDER).build();
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                boolean hasUp = dir.getParentFile() != null;
                if (hasUp && position == 0) {
                    navigate(dir.getParentFile());
                    return;
                }
                File f = entries.get(position - (hasUp ? 1 : 0));
                if (f.isDirectory()) {
                    navigate(f);
                } else if (mode == MODE_FILES) {
                    String p = f.getAbsolutePath();
                    if (!selected.remove(p)) selected.add(p);
                    adapter.notifyDataSetChanged();
                    updateBar();
                } else if (mode == MODE_VIEW) {
                    HistoryActivity.openFile(FileBrowserActivity.this, f.getAbsolutePath(), MimeTypes.fromName(f.getName()));
                }
            }
        });
        screen.setContent(list);
        if (mode != MODE_VIEW) {
            bar = k.hbox();
            bar.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            bar.setPadding(k.pagePadding(), k.dp(8), k.pagePadding(), k.dp(8));
            bar.setBackgroundColor(k.c.surfaceContainer);
            screen.addBottom(bar);
        }
        navigate(dir);
    }

    private void navigate(File d) {
        File[] children = d.listFiles();
        if (children == null) {
            k.toast(s("dialogs.noPermission.title"));
            return;
        }
        dir = d;
        entries.clear();
        Arrays.sort(children, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File f : children) {
            if (f.getName().startsWith(".")) continue;
            if (mode == MODE_FOLDER && !f.isDirectory()) continue;
            entries.add(f);
        }
        screen.appBar.subtitle(d.getAbsolutePath());
        adapter.notifyDataSetChanged();
        updateBar();
    }

    private void updateBar() {
        if (bar == null) return;
        bar.removeAllViews();
        if (mode == MODE_FOLDER) {
            confirm = k.button(s("oldy.selectFolder"), Kit.B_PRIMARY, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setResult(RESULT_OK, new Intent().putExtra(RESULT_FOLDER, dir.getAbsolutePath()));
                    finish();
                }
            });
        } else {
            confirm = k.button(s("general.add") + (selected.isEmpty() ? "" : " (" + selected.size() + ")"), Kit.B_PRIMARY, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selected.isEmpty()) return;
                    setResult(RESULT_OK, new Intent().putExtra(RESULT_PATHS, selected.toArray(new String[selected.size()])));
                    finish();
                }
            });
            confirm.setEnabled(!selected.isEmpty());
        }
        bar.addView(confirm);
    }

    private void newFolder() {
        final Field f = k.field(s("oldy.newFolder"), false);
        k.dialog().title(s("oldy.newFolder")).view(f.view).negative(s("general.cancel"), null).positive(s("general.confirm"), new Runnable() {
            @Override
            public void run() {
                String name = f.text().trim();
                if (name.length() == 0) return;
                File nd = new File(dir, name);
                if (nd.mkdirs()) navigate(nd);
                else k.toast(s("general.error"));
            }
        }).show();
    }

    @Override
    public void onBackPressed() {
        if (dir.getParentFile() != null && !dir.equals(Environment.getExternalStorageDirectory())) {
            navigate(dir.getParentFile());
            return;
        }
        super.onBackPressed();
    }
}
