package com.disgusty.oldysend.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;

import com.disgusty.oldysend.ui.kit.Action;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Installed apps as APK files (LocalSend's ApkPickerPage). */
public final class ApkPickerActivity extends BaseActivity {
    public static final String RESULT_NAMES = "names";

    private static final class AppEntry {
        String label;
        String version;
        String path;
        long size;
        boolean system;
        boolean launchable;
        ApplicationInfo info;
        Drawable icon;
    }

    private final List<AppEntry> all = new ArrayList<AppEntry>();
    private final List<AppEntry> shown = new ArrayList<AppEntry>();
    private BaseAdapter adapter;
    private boolean excludeSystem = true;
    private boolean excludeNoLaunch = true;
    private View loading;

    @Override
    protected void build() {
        screen.appBar.title(s("apkPickerPage.title")).up(finisher());
        ListView list = new ListView(this);
        k.styleList(list);
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return shown.size();
            }

            @Override
            public Object getItem(int position) {
                return shown.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                AppEntry e = shown.get(position);
                if (e.icon == null) {
                    try {
                        e.icon = e.info.loadIcon(getPackageManager());
                    } catch (Exception ex) {
                        e.icon = k.listIcon(Ic.ANDROID);
                    }
                }
                ImageView iv = k.image(e.icon);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                return k.row().leading(iv).title(e.label).summary((e.version != null ? "v" + e.version + " · " : "") + Text.fileSize(e.size)).build();
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppEntry e = shown.get(position);
                String name = e.label.replace('/', '_') + (e.version != null ? " - v" + e.version : "") + ".apk";
                setResult(RESULT_OK, new Intent().putExtra(FileBrowserActivity.RESULT_PATHS, new String[]{e.path}).putExtra(RESULT_NAMES, new String[]{name}));
                finish();
            }
        });
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(list);
        loading = k.spinner();
        frame.addView(loading, SendActivity.center());
        screen.setContent(frame);
        updateActions();
        load();
    }

    private void updateActions() {
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action(null, s("apkPickerPage.excludeSystemApps"), new Runnable() {
            @Override
            public void run() {
                excludeSystem = !excludeSystem;
                filter();
                updateActions();
            }
        }).checkable(excludeSystem));
        actions.add(new Action(null, s("apkPickerPage.excludeAppsWithoutLaunchIntent"), new Runnable() {
            @Override
            public void run() {
                excludeNoLaunch = !excludeNoLaunch;
                filter();
                updateActions();
            }
        }).checkable(excludeNoLaunch));
        screen.appBar.actions(actions);
    }

    private void load() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> list = new ArrayList<AppEntry>();
                PackageManager pm = getPackageManager();
                for (PackageInfo p : pm.getInstalledPackages(0)) {
                    ApplicationInfo ai = p.applicationInfo;
                    if (ai == null || ai.sourceDir == null) continue;
                    AppEntry e = new AppEntry();
                    e.info = ai;
                    e.label = String.valueOf(ai.loadLabel(pm));
                    e.version = p.versionName;
                    e.path = ai.sourceDir;
                    e.size = new File(ai.sourceDir).length();
                    e.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    e.launchable = !com.disgusty.oldysend.util.Sdk.atLeast(3) || Api3.launchable(pm, p.packageName);
                    list.add(e);
                }
                Collections.sort(list, new Comparator<AppEntry>() {
                    @Override
                    public int compare(AppEntry a, AppEntry b) {
                        return a.label.compareToIgnoreCase(b.label);
                    }
                });
                core.runOnMain(new Runnable() {
                    @Override
                    public void run() {
                        all.clear();
                        all.addAll(list);
                        loading.setVisibility(View.GONE);
                        filter();
                    }
                });
            }
        }, "apk-list").start();
    }

    private void filter() {
        shown.clear();
        for (AppEntry e : all) {
            if (excludeSystem && e.system) continue;
            if (excludeNoLaunch && !e.launchable) continue;
            shown.add(e);
        }
        adapter.notifyDataSetChanged();
        screen.appBar.subtitle(s("apkPickerPage.apps", "n", shown.size()));
    }

    @android.annotation.TargetApi(3)
    private static final class Api3 {
        static boolean launchable(PackageManager pm, String pkg) {
            return pm.getLaunchIntentForPackage(pkg) != null;
        }
    }
}