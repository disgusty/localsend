package com.disgusty.oldysend.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.App;
import com.disgusty.oldysend.data.Favorites;
import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.files.SendItem;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.proto.Device;
import com.disgusty.oldysend.proto.SendController;
import com.disgusty.oldysend.ui.kit.Action;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.util.Sdk;
import com.disgusty.oldysend.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Send tab: selection, pickers, nearby devices, send modes. */
final class SendTab {
    static final int REQ_FILE = 101;
    static final int REQ_FOLDER = 102;
    static final int REQ_MEDIA = 103;
    static final int REQ_APK = 104;
    static final int REQ_BROWSE_FILES = 105;
    static final int REQ_BROWSE_FOLDER = 106;

    private final MainActivity a;
    private final Kit k;
    private View root;
    private LinearLayout selectionBox;
    private LinearLayout devicesBox;
    private View scanButton;
    private TextView modeLabel;

    SendTab(MainActivity a) {
        this.a = a;
        this.k = a.k;
    }

    View view() {
        if (root != null) return root;
        LinearLayout col = k.vbox();
        col.setPadding(0, k.dp(8), 0, k.dp(24));
        selectionBox = k.vbox();
        col.addView(selectionBox, Kit.matchWrap());

        LinearLayout header = k.hbox();
        header.setPadding(0, k.dp(8), k.dp(8), 0);
        View title = k.sectionHeader(a.s("sendTab.nearbyDevices"));
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        scanButton = k.iconButton(Ic.SYNC, a.s("sendTab.scan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.core.clearDevices();
                a.core.discovery.scan();
            }
        });
        header.addView(scanButton);
        header.addView(k.iconButton(Ic.LINK, a.s("sendTab.manualSending"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manualSend();
            }
        }));
        header.addView(k.iconButton(Ic.FAVORITE, a.s("dialogs.favoriteDialog.title"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FavoritesDialog.show(a, new FavoritesDialog.Listener() {
                    @Override
                    public void onPick(Favorites.Entry e) {
                        sendToAddress(e.ip, e.port, e.https, e.fingerprint);
                    }
                });
            }
        }));
        final View[] modeAnchor = new View[1];
        modeAnchor[0] = k.iconButton(Ic.TUNE, a.s("sendTab.sendMode"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendModeMenu(modeAnchor[0]);
            }
        });
        header.addView(modeAnchor[0]);
        col.addView(header, Kit.matchWrap());
        modeLabel = k.text(Kit.T_CAPTION, "");
        modeLabel.setPadding(k.dp(16), 0, k.dp(16), k.dp(4));
        col.addView(modeLabel);

        devicesBox = k.vbox();
        col.addView(devicesBox, Kit.matchWrap());

        LinearLayout bottom = k.vbox();
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(k.pagePadding(), k.dp(16), k.pagePadding(), 0);
        bottom.addView(k.button(a.s("troubleshootPage.title"), Kit.B_TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.open(TroubleshootActivity.class);
            }
        }));
        TextView help = k.text(Kit.T_CAPTION, a.s("sendTab.help") + "\n\n" + a.s("sendTab.shareIntentInfo"));
        help.setGravity(Gravity.CENTER);
        help.setPadding(0, k.dp(12), 0, 0);
        bottom.addView(help, Kit.matchWrap());
        col.addView(bottom, Kit.matchWrap());
        root = k.scroll(col);
        return root;
    }

    void actions(List<Action> actions) {
        actions.add(new Action(Ic.SYNC, a.s("sendTab.scan"), new Runnable() {
            @Override
            public void run() {
                a.core.clearDevices();
                a.core.discovery.scan();
            }
        }));
        actions.add(new Action(Ic.LINK, a.s("sendTab.manualSending"), new Runnable() {
            @Override
            public void run() {
                manualSend();
            }
        }));
        actions.add(new Action(Ic.DELETE, a.s("selectedFilesPage.deleteAll"), new Runnable() {
            @Override
            public void run() {
                App.selection.clear();
                a.core.changed(Core.CHANGED_SEND);
            }
        }));
    }

    // ---------------------------------------------------------------- refresh

    void refresh(int what) {
        if (root == null) return;
        if ((what & (Core.CHANGED_SEND | Core.CHANGED_HISTORY)) != 0 || what == -1) buildSelection();
        if ((what & (Core.CHANGED_DEVICES | Core.CHANGED_SEND | Core.CHANGED_SCAN)) != 0 || what == -1) buildDevices();
        String mode = a.core.settings.sendMode();
        modeLabel.setText(a.s("sendTab.sendMode") + ": " + modeName(mode));
        scanButton.setEnabled(!a.core.discovery.scanning());
        if (Sdk.atLeast(11)) scanButton.setAlpha(a.core.discovery.scanning() ? 0.4f : 1f);
    }

    private String modeName(String mode) {
        if (Settings.SEND_MULTIPLE.equals(mode)) return a.s("sendTab.sendModes.multiple");
        if (Settings.SEND_LINK.equals(mode)) return a.s("sendTab.sendModes.link");
        return a.s("sendTab.sendModes.single");
    }

    private void buildSelection() {
        selectionBox.removeAllViews();
        selectionBox.addView(k.sectionHeader(a.s("sendTab.selection.title")));
        if (App.selection.isEmpty()) {
            selectionBox.addView(pickerGrid(), Kit.matchWrap());
            return;
        }
        ViewGroup card = k.card(false);
        card.setPadding(k.dp(16), k.dp(12), k.dp(16), k.dp(8));
        long size = 0;
        for (SendItem i : App.selection) size += i.size;
        card.addView(k.text(Kit.T_BODY, a.s("sendTab.selection.files", "files", App.selection.size())));
        card.addView(k.text(Kit.T_SECONDARY, a.s("sendTab.selection.size", "size", Text.fileSize(size))));
        LinearLayout strip = k.hbox();
        strip.setPadding(0, k.dp(8), 0, k.dp(8));
        int thumb = k.dp(56);
        int shown = 0;
        for (SendItem item : App.selection) {
            if (shown++ >= 30) break;
            View v = itemThumb(item, thumb);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(thumb, thumb);
            p.rightMargin = k.dp(8);
            strip.addView(v, p);
        }
        card.addView(Compat.hscroll(a, strip), Kit.matchWrap());
        LinearLayout buttons = k.hbox();
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        buttons.addView(k.button(a.s("general.edit"), Kit.B_TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.open(SelectedFilesActivity.class);
            }
        }));
        buttons.addView(k.space(8));
        buttons.addView(k.button(a.s("general.add"), Kit.B_PRIMARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addDialog();
            }
        }));
        card.addView(buttons, Kit.matchWrap());
        LinearLayout.LayoutParams p = Kit.matchWrap();
        p.setMargins(k.dp(12), 0, k.dp(12), k.dp(8));
        selectionBox.addView(card, p);
    }

    View itemThumb(SendItem item, int size) {
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(k.c.surfaceContainerHighest);
        iv.setImageDrawable(k.icon(UiUtil.fileIcon(item.mime, item.name), k.iconColor(), 28));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        if (item.mime != null && item.mime.startsWith("image/")) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            UiUtil.thumbnail(a, item, iv, size);
        }
        return iv;
    }

    private View pickerGrid() {
        String[] labels = {a.s("sendTab.picker.file"), a.s("sendTab.picker.folder"), a.s("sendTab.picker.media"),
                a.s("sendTab.picker.text"), a.s("sendTab.picker.app"), a.s("sendTab.picker.clipboard")};
        Ic[] icons = {Ic.INSERT_DRIVE_FILE, Ic.FOLDER, Ic.PHOTO_LIBRARY, Ic.SUBJECT, Ic.APPS, Ic.CONTENT_PASTE};
        LinearLayout grid = k.vbox();
        grid.setPadding(k.dp(12), 0, k.dp(12), k.dp(8));
        LinearLayout row = null;
        for (int i = 0; i < labels.length; i++) {
            if (i % 3 == 0) {
                row = k.hbox();
                grid.addView(row, Kit.matchWrap());
            }
            final int index = i;
            View tile = k.tile(icons[i], labels[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick(index);
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            p.setMargins(k.dp(4), k.dp(4), k.dp(4), k.dp(4));
            row.addView(tile, p);
        }
        return grid;
    }

    private void addDialog() {
        final String[] labels = {a.s("sendTab.picker.file"), a.s("sendTab.picker.folder"), a.s("sendTab.picker.media"),
                a.s("sendTab.picker.text"), a.s("sendTab.picker.app"), a.s("sendTab.picker.clipboard")};
        k.dialog().title(a.s("dialogs.addFile.title")).items(labels, new com.disgusty.oldysend.ui.kit.DialogBuilder.ItemListener() {
            @Override
            public void onItem(int index) {
                pick(index);
            }
        }).negative(a.s("general.cancel"), null).show();
    }

    // ---------------------------------------------------------------- pickers

    private void pick(int index) {
        switch (index) {
            case 0:
                pickFiles();
                break;
            case 1:
                pickFolder();
                break;
            case 2:
                pickMedia();
                break;
            case 3:
                MessageDialog.show(a, new MessageDialog.Listener() {
                    @Override
                    public void onMessage(String text) {
                        App.selection.add(SendItem.ofText(text));
                        a.core.changed(Core.CHANGED_SEND);
                    }
                });
                break;
            case 4:
                a.startActivityForResult(new Intent(a, ApkPickerActivity.class), REQ_APK);
                break;
            default:
                paste();
                break;
        }
    }

    private void pickFiles() {
        if (Sdk.atLeast(19)) {
            Intent i = new Intent("android.intent.action.OPEN_DOCUMENT");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
            if (start(i, REQ_FILE)) return;
        }
        // Android 1.x–4.3: file managers rarely answered GET_CONTENT, so OldySend brings its own browser.
        a.startActivityForResult(new Intent(a, FileBrowserActivity.class).putExtra(FileBrowserActivity.EXTRA_MODE, FileBrowserActivity.MODE_FILES), REQ_BROWSE_FILES);
    }

    private void pickFolder() {
        if (Sdk.atLeast(21)) {
            if (start(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"), REQ_FOLDER)) return;
        }
        a.startActivityForResult(new Intent(a, FileBrowserActivity.class).putExtra(FileBrowserActivity.EXTRA_MODE, FileBrowserActivity.MODE_FOLDER), REQ_BROWSE_FOLDER);
    }

    private void pickMedia() {
        Intent i;
        if (Sdk.atLeast(33)) {
            i = new Intent("android.provider.action.PICK_IMAGES");
            i.putExtra("android.provider.extra.PICK_IMAGES_MAX", 100);
            if (start(i, REQ_MEDIA)) return;
        }
        i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        if (Sdk.atLeast(19)) i.putExtra("android.intent.extra.MIME_TYPES", new String[]{"image/*", "video/*"});
        if (Sdk.atLeast(18)) i.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
        if (start(Intent.createChooser(i, a.s("sendTab.picker.media")), REQ_MEDIA)) return;
        a.startActivityForResult(new Intent(a, FileBrowserActivity.class).putExtra(FileBrowserActivity.EXTRA_MODE, FileBrowserActivity.MODE_FILES), REQ_BROWSE_FILES);
    }

    private boolean start(Intent i, int code) {
        try {
            a.startActivityForResult(i, code);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        } catch (SecurityException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private void paste() {
        String text = null;
        if (Sdk.atLeast(11)) text = Api11.clip(a);
        else {
            android.text.ClipboardManager cm = (android.text.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasText()) text = cm.getText().toString();
        }
        if (text == null || text.length() == 0) {
            k.toast(a.s("general.noItemInClipboard"));
            return;
        }
        App.selection.add(SendItem.ofText(text));
        a.core.changed(Core.CHANGED_SEND);
    }

    boolean onResult(int requestCode, int resultCode, Intent data) {
        if (requestCode < REQ_FILE || requestCode > REQ_BROWSE_FOLDER) return false;
        if (resultCode != Activity.RESULT_OK || data == null) return true;
        try {
            switch (requestCode) {
                case REQ_FILE:
                case REQ_MEDIA:
                    for (Uri u : uris(data)) {
                        if (Sdk.atLeast(19)) Api19.persist(a, u);
                        App.selection.add(SendItem.ofUri(a, u, null));
                    }
                    break;
                case REQ_FOLDER:
                    if (Sdk.atLeast(21) && data.getData() != null) Api21.addTree(a, data.getData());
                    break;
                case REQ_APK:
                case REQ_BROWSE_FILES:
                    String[] paths = data.getStringArrayExtra(FileBrowserActivity.RESULT_PATHS);
                    String[] names = data.getStringArrayExtra(ApkPickerActivity.RESULT_NAMES);
                    if (paths != null) {
                        for (int i = 0; i < paths.length; i++) {
                            SendItem item = SendItem.ofFile(new File(paths[i]), null);
                            if (names != null && i < names.length && names[i] != null) {
                                item.name = names[i];
                                item.mime = com.disgusty.oldysend.files.MimeTypes.APK;
                            }
                            App.selection.add(item);
                        }
                    }
                    break;
                case REQ_BROWSE_FOLDER:
                    String folder = data.getStringExtra(FileBrowserActivity.RESULT_FOLDER);
                    if (folder != null) addFolder(new File(folder), new File(folder).getName());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            k.toast(a.s("general.error") + ": " + e.getMessage());
        }
        a.core.changed(Core.CHANGED_SEND);
        return true;
    }

    private void addFolder(File dir, String prefix) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) addFolder(f, prefix + "/" + f.getName());
            else App.selection.add(SendItem.ofFile(f, prefix + "/" + f.getName()));
        }
    }

    private static List<Uri> uris(Intent data) {
        List<Uri> out = new ArrayList<Uri>();
        if (Sdk.atLeast(16)) Api16.clipUris(data, out);
        if (out.isEmpty() && data.getData() != null) out.add(data.getData());
        return out;
    }

    // ---------------------------------------------------------------- devices

    private void buildDevices() {
        devicesBox.removeAllViews();
        List<Device> devices = a.core.devices();
        if (devices.isEmpty()) {
            View placeholder = deviceRow(null, null);
            if (Sdk.atLeast(11)) placeholder.setAlpha(0.3f);
            devicesBox.addView(placeholder, Kit.matchWrap());
            return;
        }
        String mode = a.core.settings.sendMode();
        for (final Device d : devices) {
            String status = null;
            if (Settings.SEND_MULTIPLE.equals(mode)) {
                for (SendController.Session s : a.core.send.sessions()) {
                    if (s.target.ip.equals(d.ip)) status = SendActivity.statusText(a, s);
                }
            }
            devicesBox.addView(deviceRow(d, status), Kit.matchWrap());
        }
    }

    View deviceRow(final Device d, String status) {
        ViewGroup card = k.card(d != null);
        LinearLayout row = k.hbox();
        row.setPadding(k.dp(16), k.dp(12), k.dp(8), k.dp(12));
        ImageView icon = k.image(k.icon(UiUtil.deviceIcon(d == null ? Device.TYPE_MOBILE : d.deviceType), k.iconColor(), 40));
        row.addView(icon, new LinearLayout.LayoutParams(k.dp(48), k.dp(48)));
        LinearLayout texts = k.vbox();
        texts.setPadding(k.dp(16), 0, 0, 0);
        TextView name = k.text(Kit.T_SUBTITLE, d == null ? "                    " : d.alias);
        k.ellipsize(name, 1);
        texts.addView(name);
        LinearLayout chips = k.hbox();
        chips.setPadding(0, k.dp(4), 0, 0);
        if (d != null) {
            if (status != null) {
                chips.addView(k.text(Kit.T_SECONDARY, status));
            } else {
                chips.addView(k.chip(UiUtil.visualId(d.ip)));
                if (d.deviceModel != null) {
                    chips.addView(k.space(6));
                    chips.addView(k.chip(d.deviceModel));
                }
                if (a.core.favorites.isFavorite(d.fingerprint)) {
                    chips.addView(k.space(6));
                    chips.addView(k.chip("★"));
                }
            }
        } else {
            chips.addView(k.chip("    "));
        }
        texts.addView(chips);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (d != null) {
            final View[] more = new View[1];
            more[0] = k.iconButton(Ic.MORE_VERT, a.s("deviceDetailsPage.title"), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deviceMenu(more[0], d);
                }
            });
            row.addView(more[0]);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendTo(d);
                }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    deviceMenu(v, d);
                    return true;
                }
            });
        }
        card.addView(row, Kit.matchWrap());
        LinearLayout wrap = k.vbox();
        wrap.setPadding(k.dp(12), k.dp(4), k.dp(12), k.dp(4));
        wrap.addView(card, Kit.matchWrap());
        return wrap;
    }

    private void deviceMenu(View anchor, final Device d) {
        List<Action> acts = new ArrayList<Action>();
        final boolean fav = a.core.favorites.isFavorite(d.fingerprint);
        acts.add(new Action(Ic.FAVORITE, fav ? a.s("dialogs.favoriteDeleteDialog.title") : a.s("dialogs.favoriteEditDialog.titleAdd"), new Runnable() {
            @Override
            public void run() {
                if (fav) {
                    Favorites.Entry e = a.core.favorites.byFingerprint(d.fingerprint);
                    if (e != null) a.core.favorites.remove(e.id);
                    a.core.changed(Core.CHANGED_DEVICES);
                } else {
                    FavoritesDialog.edit(a, null, d);
                }
            }
        }));
        acts.add(new Action(Ic.INFO, a.s("deviceDetailsPage.title"), new Runnable() {
            @Override
            public void run() {
                DeviceDetailsActivity.open(a, d);
            }
        }));
        k.menu(anchor, d.alias, acts);
    }

    private void sendModeMenu(View anchor) {
        String mode = a.core.settings.sendMode();
        List<Action> acts = new ArrayList<Action>();
        final String[] modes = {Settings.SEND_SINGLE, Settings.SEND_MULTIPLE, Settings.SEND_LINK};
        for (final String m : modes) {
            acts.add(new Action(Ic.CHECK_CIRCLE, modeName(m), new Runnable() {
                @Override
                public void run() {
                    a.core.settings.setSendMode(m);
                    if (Settings.SEND_LINK.equals(m)) WebShareActivity.openShare(a);
                    a.core.changed(Core.CHANGED_SEND);
                }
            }).checkable(m.equals(mode)));
        }
        acts.add(new Action(Ic.HELP, a.s("sendTab.sendModeHelp"), new Runnable() {
            @Override
            public void run() {
                k.dialog().title(a.s("dialogs.sendModeHelp.title"))
                        .message(a.s("sendTab.sendModes.single") + "\n" + a.s("dialogs.sendModeHelp.single") + "\n\n"
                                + a.s("sendTab.sendModes.multiple") + "\n" + a.s("dialogs.sendModeHelp.multiple") + "\n\n"
                                + a.s("sendTab.sendModes.link") + "\n" + a.s("dialogs.sendModeHelp.link"))
                        .positive(a.s("general.close"), null).show();
            }
        }));
        k.menu(anchor, a.s("sendTab.sendMode"), acts);
    }

    // ---------------------------------------------------------------- sending

    void sendTo(Device d) {
        if (App.selection.isEmpty()) {
            k.dialog().title(a.s("dialogs.noFiles.title")).message(a.s("dialogs.noFiles.content")).positive(a.s("general.close"), null).show();
            return;
        }
        String mode = a.core.settings.sendMode();
        if (Settings.SEND_LINK.equals(mode)) {
            WebShareActivity.openShare(a);
            return;
        }
        SendController.Session s = a.core.send.start(d, new ArrayList<SendItem>(App.selection));
        if (Settings.SEND_SINGLE.equals(mode)) {
            a.startActivity(new Intent(a, SendActivity.class).putExtra(SendActivity.EXTRA_SESSION, s.id));
        }
    }

    private void manualSend() {
        AddressDialog.show(a, new AddressDialog.Listener() {
            @Override
            public void onAddress(String ip) {
                sendToAddress(ip, a.core.settings.port(), a.core.https(), null);
            }
        });
    }

    /** Probes the address (register) and sends to it, like LocalSend's manual sending. */
    /** Debug builds only (see MainActivity.handleDebugSend). */
    static String debugDeadIp;

    void sendToAddress(final String ip, final int port, final boolean https, final String fingerprint) {
        final com.disgusty.oldysend.ui.kit.DialogBuilder progress = k.dialog().view(k.spinner()).cancelable(true);
        progress.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Device d = a.core.discovery.probe(ip, port, https, 5000);
                if (d == null) d = a.core.discovery.probe(ip, port, !https, 5000);
                final Device found = d;
                a.core.runOnMain(new Runnable() {
                    @Override
                    public void run() {
                        progress.dismiss();
                        if (found == null) {
                            k.dialog().title(a.s("general.error")).message(a.s("troubleshootPage.noConnection.symptom"))
                                    .positive(a.s("general.close"), null).show();
                            return;
                        }
                        if (fingerprint != null && found.fingerprint != null && !fingerprint.equalsIgnoreCase(found.fingerprint)) {
                            k.toast(a.s("general.error"));
                            return;
                        }
                        if (com.disgusty.oldysend.BuildConfig.DEBUG && debugDeadIp != null) {
                            // Test hook: pretend the device was first seen at an unreachable address.
                            found.otherIps.add(found.ip);
                            found.ip = debugDeadIp;
                            debugDeadIp = null;
                        }
                        sendTo(found);
                    }
                });
            }
        }, "manual-probe").start();
    }

    // ---------------------------------------------------------------- API helpers

    @TargetApi(11)
    private static final class Api11 {
        static String clip(Context c) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return null;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence t = clip.getItemAt(0).coerceToText(c);
            return t == null ? null : t.toString();
        }
    }

    @TargetApi(16)
    private static final class Api16 {
        static void clipUris(Intent data, List<Uri> out) {
            ClipData clip = data.getClipData();
            if (clip == null) return;
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) out.add(u);
            }
        }
    }

    @TargetApi(19)
    private static final class Api19 {
        static void persist(Context c, Uri u) {
            try {
                c.getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                // Not every provider grants persistable permissions; the selection works within this session.
            }
        }
    }

    @TargetApi(21)
    private static final class Api21 {
        static void addTree(Context c, Uri tree) {
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            String name = rootId.substring(rootId.lastIndexOf(':') + 1);
            if (name.lastIndexOf('/') >= 0) name = name.substring(name.lastIndexOf('/') + 1);
            if (name.length() == 0) name = "folder";
            walk(c, tree, rootId, name);
        }

        private static void walk(Context c, Uri tree, String docId, String prefix) {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
            Cursor cur = null;
            try {
                cur = c.getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null);
                while (cur != null && cur.moveToNext()) {
                    String id = cur.getString(0);
                    String name = cur.getString(1);
                    String mime = cur.getString(2);
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        walk(c, tree, id, prefix + "/" + name);
                    } else {
                        SendItem item = new SendItem();
                        item.uri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                        item.name = prefix + "/" + name;
                        item.mime = mime;
                        item.size = cur.isNull(3) ? 0 : cur.getLong(3);
                        item.modified = cur.isNull(4) ? 0 : cur.getLong(4);
                        App.selection.add(item);
                    }
                }
            } finally {
                if (cur != null) cur.close();
            }
        }
    }

}
