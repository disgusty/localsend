package com.disgusty.oldysend.ui;

import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;

import com.disgusty.oldysend.data.Favorites;
import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.net.NetUtil;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.proto.Device;
import com.disgusty.oldysend.ui.kit.Field;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.Kit;

import java.util.List;

/** Favorites list, add/edit and delete dialogs. */
final class FavoritesDialog {
    interface Listener {
        void onPick(Favorites.Entry e);
    }

    private FavoritesDialog() {
    }

    static void show(final BaseActivity a, final Listener l) {
        final Kit k = a.k;
        LinearLayout box = k.vbox();
        List<Favorites.Entry> all = a.core.favorites.all();
        final com.disgusty.oldysend.ui.kit.DialogBuilder d = k.dialog().title(a.s("dialogs.favoriteDialog.title"));
        if (all.isEmpty()) {
            box.addView(k.text(Kit.T_BODY, a.s("dialogs.favoriteDialog.noFavorites")));
        }
        for (final Favorites.Entry e : all) {
            LinearLayout row = k.hbox();
            View main = k.row().icon(Ic.DEVICES).title(e.alias).summary(e.ip + ":" + e.port).onClick(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    l.onPick(e);
                }
            }).build();
            row.addView(main, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(k.iconButton(Ic.EDIT, a.s("general.edit"), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    edit(a, e, null);
                }
            }));
            row.addView(k.iconButton(Ic.DELETE, a.s("general.delete"), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    k.dialog().title(a.s("dialogs.favoriteDeleteDialog.title"))
                            .message(a.s("dialogs.favoriteDeleteDialog.content", "name", e.alias))
                            .negative(a.s("general.cancel"), null)
                            .positive(a.s("general.delete"), new Runnable() {
                                @Override
                                public void run() {
                                    a.core.favorites.remove(e.id);
                                    a.core.changed(Core.CHANGED_DEVICES);
                                }
                            }).show();
                }
            }));
            box.addView(row, Kit.matchWrap());
        }
        d.view(box).neutral(a.s("dialogs.favoriteDialog.addFavorite"), new Runnable() {
            @Override
            public void run() {
                edit(a, null, null);
            }
        }).positive(a.s("general.close"), null).show();
    }

    /** Add (entry == null) or edit a favorite; {@code device} pre-fills from a discovered device. */
    static void edit(final BaseActivity a, final Favorites.Entry entry, final Device device) {
        final Kit k = a.k;
        LinearLayout box = k.vbox();
        final Field name = k.field(a.s("dialogs.favoriteEditDialog.name"), false);
        final Field ip = k.field(a.s("dialogs.favoriteEditDialog.ip"), false);
        final Field port = k.field(a.s("dialogs.favoriteEditDialog.port"), false);
        com.disgusty.oldysend.ui.Compat.inputType(port.edit, InputType.TYPE_CLASS_NUMBER);
        name.edit.setHint(a.s("dialogs.favoriteEditDialog.auto"));
        if (entry != null) {
            if (entry.customAlias) name.text(entry.alias);
            ip.text(entry.ip);
            port.text(String.valueOf(entry.port));
        } else if (device != null) {
            ip.text(device.ip);
            port.text(String.valueOf(device.port));
        } else {
            port.text(String.valueOf(Settings.DEFAULT_PORT));
        }
        box.addView(name.view, Kit.matchWrap());
        box.addView(k.space(8));
        box.addView(ip.view, Kit.matchWrap());
        box.addView(k.space(8));
        box.addView(port.view, Kit.matchWrap());
        final com.disgusty.oldysend.ui.kit.DialogBuilder d = k.dialog()
                .title(entry == null ? a.s("dialogs.favoriteEditDialog.titleAdd") : a.s("dialogs.favoriteEditDialog.titleEdit"))
                .view(box).keepOpen();
        d.negative(a.s("general.cancel"), new Runnable() {
            @Override
            public void run() {
                d.dismiss();
            }
        }).positive(a.s("general.save"), new Runnable() {
            @Override
            public void run() {
                final String ipText = ip.text().trim();
                int p;
                try {
                    p = Integer.parseInt(port.text().trim());
                } catch (NumberFormatException e) {
                    port.setError(a.s("general.error"));
                    return;
                }
                if (!NetUtil.isValidIpv4(ipText)) {
                    ip.setError(a.s("general.error"));
                    return;
                }
                d.dismiss();
                final int finalPort = p;
                final String customName = name.text().trim();
                if (device != null) {
                    save(a, entry, device, customName);
                    return;
                }
                // Resolve fingerprint and alias by registering with the device.
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Device found = a.core.discovery.probe(ipText, finalPort, a.core.https(), 4000);
                        if (found == null) found = a.core.discovery.probe(ipText, finalPort, !a.core.https(), 4000);
                        if (found == null) {
                            found = new Device();
                            found.ip = ipText;
                            found.port = finalPort;
                            found.https = a.core.https();
                            found.alias = ipText;
                            found.fingerprint = entry != null ? entry.fingerprint : "";
                        }
                        final Device f = found;
                        a.core.runOnMain(new Runnable() {
                            @Override
                            public void run() {
                                save(a, entry, f, customName);
                            }
                        });
                    }
                }, "favorite-probe").start();
            }
        }).show();
    }

    private static void save(BaseActivity a, Favorites.Entry entry, Device d, String customName) {
        Favorites.Entry e = entry != null ? entry : new Favorites.Entry();
        e.fingerprint = d.fingerprint == null ? "" : d.fingerprint;
        e.ip = d.ip;
        e.port = d.port;
        e.https = d.https;
        e.customAlias = customName.length() > 0;
        e.alias = e.customAlias ? customName : d.alias;
        a.core.favorites.put(e);
        a.core.changed(Core.CHANGED_DEVICES);
    }
}
