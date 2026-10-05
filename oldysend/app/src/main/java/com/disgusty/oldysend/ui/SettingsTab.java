package com.disgusty.oldysend.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;

import com.disgusty.oldysend.App;
import com.disgusty.oldysend.data.Settings;
import com.disgusty.oldysend.files.Storage;
import com.disgusty.oldysend.i18n.I18n;
import com.disgusty.oldysend.proto.Core;
import com.disgusty.oldysend.proto.Device;
import com.disgusty.oldysend.service.ServerService;
import com.disgusty.oldysend.ui.kit.Action;
import com.disgusty.oldysend.ui.kit.DialogBuilder;
import com.disgusty.oldysend.ui.kit.Field;
import com.disgusty.oldysend.ui.kit.Ic;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.ui.kit.Palettes;
import com.disgusty.oldysend.ui.kit.Toggle;
import com.disgusty.oldysend.util.Sdk;
import com.disgusty.oldysend.util.Text;

import java.util.List;

/** Settings tab, following LocalSend's sections plus OldySend's appearance options. */
final class SettingsTab {
    static final int REQ_DESTINATION = 201;
    static final int REQ_BROWSE_DESTINATION = 202;

    private final MainActivity a;
    private final Kit k;
    private final Settings st;
    private LinearLayout col;
    private View root;
    private String lastServerState;

    SettingsTab(MainActivity a) {
        this.a = a;
        this.k = a.k;
        this.st = a.core.settings;
    }

    View view() {
        if (root == null) {
            col = k.vbox();
            col.setPadding(0, 0, 0, k.dp(32));
            root = k.scroll(col);
        }
        return root;
    }

    void actions(List<Action> actions) {
        actions.add(new Action(Ic.INFO, a.s("aboutPage.title"), new Runnable() {
            @Override
            public void run() {
                a.open(AboutActivity.class);
            }
        }));
    }

    void refresh(int what) {
        if (col == null) return;
        String serverState = a.core.serverRunning() + "/" + a.core.serverStarting() + "/" + a.core.serverError();
        if (what != -1 && (what & Core.CHANGED_SERVER) == 0) return;
        if (what != -1 && serverState.equals(lastServerState)) return;
        lastServerState = serverState;
        rebuild();
    }

    private void rebuild() {
        int scroll = root.getScrollY();
        col.removeAllViews();
        general();
        receive();
        send();
        network();
        other();
        root.scrollTo(0, scroll);
    }

    // ---------------------------------------------------------------- helpers

    private void section(String title) {
        col.addView(k.sectionHeader(title), Kit.matchWrap());
    }

    private void add(View v) {
        col.addView(v, Kit.matchWrap());
        if (needsDividers()) col.addView(k.divider());
    }

    private boolean needsDividers() {
        return Settings.STYLE_CLASSIC.equals(k.style) || Settings.STYLE_HOLO.equals(k.style);
    }

    private void value(Ic icon, String title, String value, View.OnClickListener l) {
        add(k.row().icon(icon).title(title).summary(value).onClick(l).build());
    }

    private interface BoolSetter {
        void set(boolean v);
    }

    private void bool(Ic icon, String title, String summary, boolean value, final BoolSetter setter) {
        final Toggle tg = k.toggle(Settings.STYLE_CLASSIC.equals(k.style) ? Kit.CHECKBOX : Kit.SWITCH).passive();
        tg.setChecked(value);
        add(k.row().icon(icon).title(title).summary(summary).trailing(tg).onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tg.toggle();
                setter.set(tg.isChecked());
            }
        }).build());
    }

    private interface Choice {
        void chosen(int index);
    }

    private void choose(String title, String[] labels, int current, final Choice c) {
        k.dialog().title(title).singleChoice(labels, current, new DialogBuilder.ItemListener() {
            @Override
            public void onItem(int index) {
                c.chosen(index);
            }
        }).negative(a.s("general.cancel"), null).show();
    }

    private void appearanceChanged() {
        App.appearanceChanged();
        a.rebuild();
    }

    // ---------------------------------------------------------------- general

    private void general() {
        section(a.s("settingsTab.general.title"));
        final String[] styleIds = {Settings.STYLE_AUTO, Settings.STYLE_CLASSIC, Settings.STYLE_HOLO, Settings.STYLE_MD1, Settings.STYLE_MD3};
        final String[] styleNames = new String[styleIds.length];
        int curStyle = 0;
        for (int i = 0; i < styleIds.length; i++) {
            styleNames[i] = a.s("oldy.styles." + styleIds[i]);
            if (styleIds[i].equals(st.style())) curStyle = i;
        }
        final int cs = curStyle;
        value(Ic.PALETTE, a.s("oldy.style"), styleNames[curStyle], new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choose(a.s("oldy.style"), styleNames, cs, new Choice() {
                    @Override
                    public void chosen(int index) {
                        st.setStyle(styleIds[index]);
                        appearanceChanged();
                    }
                });
            }
        });

        bool(Ic.APPS, a.s("oldy.iconFollowsStyle"), a.s("oldy.iconFollowsStyleHint"), st.iconFollowsStyle(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setIconFollowsStyle(v);
            }
        });

        final String[] bIds = {Settings.BRIGHTNESS_SYSTEM, Settings.BRIGHTNESS_LIGHT, Settings.BRIGHTNESS_DARK};
        final String[] bNames = {a.s("settingsTab.general.brightnessOptions.system"), a.s("settingsTab.general.brightnessOptions.light"),
                a.s("settingsTab.general.brightnessOptions.dark")};
        final int cb = indexOf(bIds, st.brightness());
        value(Ic.BRIGHTNESS_6, a.s("settingsTab.general.brightness"), bNames[cb], new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choose(a.s("settingsTab.general.brightness"), bNames, cb, new Choice() {
                    @Override
                    public void chosen(int index) {
                        st.setBrightness(bIds[index]);
                        appearanceChanged();
                    }
                });
            }
        });

        // Color applies to the Material styles (Holo is always blue, Classic always orange — like the originals).
        String eff = st.effectiveStyle();
        if (Settings.STYLE_MD1.equals(eff) || Settings.STYLE_MD3.equals(eff)) {
            final String[] cIds = {Settings.COLOR_SYSTEM, Settings.COLOR_OLED, Settings.COLOR_CUSTOM};
            final String[] cNames = {a.s(Settings.STYLE_MD3.equals(eff) && Sdk.atLeast(31) ? "oldy.dynamicColor" : "settingsTab.general.colorOptions.system"),
                    a.s("settingsTab.general.colorOptions.oled"), a.s("settingsTab.general.colorOptions.custom")};
            final int cc = indexOf(cIds, st.colorMode());
            String label = cNames[cc];
            if (cc == 2) label += " — " + Palettes.NAMES[Math.min(st.customColor(), Palettes.NAMES.length - 1)];
            value(Ic.COLOR_LENS, a.s("settingsTab.general.color"), label, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    choose(a.s("settingsTab.general.color"), cNames, cc, new Choice() {
                        @Override
                        public void chosen(int index) {
                            st.setColorMode(cIds[index]);
                            if (index == 2) {
                                choose(a.s("oldy.colorPreset"), Palettes.NAMES, st.customColor(), new Choice() {
                                    @Override
                                    public void chosen(int preset) {
                                        st.setCustomColor(preset);
                                        appearanceChanged();
                                    }
                                });
                            } else {
                                appearanceChanged();
                            }
                        }
                    });
                }
            });
        }

        final List<String> ids = I18n.ids();
        final String[] names = new String[ids.size() + 1];
        names[0] = a.s("settingsTab.general.languageOptions.system");
        int cl = 0;
        for (int i = 0; i < ids.size(); i++) {
            names[i + 1] = I18n.nativeName(ids.get(i));
            if (ids.get(i).equals(st.locale())) cl = i + 1;
        }
        final int curLang = cl;
        value(Ic.TRANSLATE, a.s("settingsTab.general.language"), names[cl], new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choose(a.s("settingsTab.general.language"), names, curLang, new Choice() {
                    @Override
                    public void chosen(int index) {
                        st.setLocale(index == 0 ? I18n.SYSTEM : ids.get(index - 1));
                        App.reloadLanguage();
                        a.rebuild();
                    }
                });
            }
        });
        bool(Ic.SPEED, a.s("settingsTab.general.animations"), null, st.animations(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setAnimations(v);
                App.appearanceChanged();
            }
        });
    }

    // ---------------------------------------------------------------- receive

    private void receive() {
        section(a.s("settingsTab.receive.title"));
        bool(Ic.FLASH_ON, a.s("settingsTab.receive.quickSave"), null, st.quickSaveOn(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setQuickSaveOn(v);
            }
        });
        bool(Ic.FAVORITE, a.s("settingsTab.receive.quickSaveFromFavorites"), null, st.quickSaveFromFavorites(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setQuickSaveFromFavorites(v);
            }
        });
        final String pin = st.receivePin();
        bool(Ic.PASSWORD, a.s("settingsTab.receive.requirePin"), pin, pin != null, new BoolSetter() {
            @Override
            public void set(boolean v) {
                if (!v) {
                    st.setReceivePin(null);
                    rebuild();
                    return;
                }
                askText(a.s("settingsTab.receive.requirePin"), "", InputType.TYPE_CLASS_NUMBER, new TextResult() {
                    @Override
                    public void done(String text) {
                        st.setReceivePin(text.length() == 0 ? null : text);
                        rebuild();
                    }
                });
            }
        });
        value(Ic.FOLDER, a.s("settingsTab.receive.destination"),
                st.destination() == null ? a.s("settingsTab.receive.downloads") + " " + Storage.describe(a, null) : Storage.describe(a, st.destination()),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        destinationMenu();
                    }
                });
        bool(Ic.PHOTO_LIBRARY, a.s("settingsTab.receive.saveToGallery"), null, st.saveToGallery(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setSaveToGallery(v);
            }
        });
        bool(Ic.DONE_ALL, a.s("settingsTab.receive.autoFinish"), null, st.autoFinish(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setAutoFinish(v);
            }
        });
        bool(Ic.HISTORY, a.s("settingsTab.receive.saveToHistory"), null, st.saveToHistory(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setSaveToHistory(v);
            }
        });
        if (st.advancedSettings()) {
            bool(Ic.VERIFIED_USER, a.s("settingsTab.receive.verifyChecksums"), null, st.verifyChecksums(), new BoolSetter() {
                @Override
                public void set(boolean v) {
                    st.setVerifyChecksums(v);
                }
            });
        }
    }

    private void destinationMenu() {
        String[] items = {a.s("settingsTab.receive.downloads"), a.s("oldy.chooseFolder")};
        k.dialog().title(a.s("settingsTab.receive.destination")).items(items, new DialogBuilder.ItemListener() {
            @Override
            public void onItem(int index) {
                if (index == 0) {
                    st.setDestination(null);
                    rebuild();
                    return;
                }
                if (Sdk.atLeast(21)) {
                    try {
                        a.startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"), REQ_DESTINATION);
                        return;
                    } catch (Exception ignored) {
                        // fall back to the built-in browser
                    }
                }
                a.startActivityForResult(new Intent(a, FileBrowserActivity.class).putExtra(FileBrowserActivity.EXTRA_MODE, FileBrowserActivity.MODE_FOLDER),
                        REQ_BROWSE_DESTINATION);
            }
        }).negative(a.s("general.cancel"), null).show();
    }

    boolean onResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_DESTINATION && requestCode != REQ_BROWSE_DESTINATION) return false;
        if (resultCode != Activity.RESULT_OK || data == null) return true;
        if (requestCode == REQ_DESTINATION && data.getData() != null) {
            if (Sdk.atLeast(21)) Api21.persist(a, data.getData());
            st.setDestination(data.getData().toString());
        } else if (requestCode == REQ_BROWSE_DESTINATION) {
            String folder = data.getStringExtra(FileBrowserActivity.RESULT_FOLDER);
            if (folder != null) st.setDestination(folder);
        }
        rebuild();
        return true;
    }

    // ---------------------------------------------------------------- send

    private void send() {
        section(a.s("settingsTab.send.title"));
        bool(Ic.LINK, a.s("settingsTab.send.shareViaLinkAutoAccept"), null, st.shareViaLinkAutoAccept(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setShareViaLinkAutoAccept(v);
            }
        });
        if (st.advancedSettings()) {
            bool(Ic.VERIFIED_USER, a.s("settingsTab.send.createChecksums"), null, st.createChecksums(), new BoolSetter() {
                @Override
                public void set(boolean v) {
                    st.setCreateChecksums(v);
                }
            });
        }
    }

    // ---------------------------------------------------------------- network

    private void network() {
        section(a.s("settingsTab.network.title"));
        final Core core = a.core;
        String state = core.serverStarting() ? "…" : core.serverRunning()
                ? a.s("general.online") + " (" + (core.https() ? "HTTPS" : "HTTP") + ", " + core.port() + ")"
                : a.s("general.offline") + (core.serverError() != null ? " — " + core.serverError() : "");
        LinearLayout buttons = k.hbox();
        buttons.addView(k.iconButton(Ic.RESTART_ALT, a.s("general.restart"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                core.restartServer();
            }
        }));
        buttons.addView(k.iconButton(core.serverRunning() ? Ic.STOP : Ic.PLAY_ARROW, core.serverRunning() ? a.s("general.stop") : a.s("general.start"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (core.serverRunning()) core.stopServer();
                        else core.startServer();
                    }
                }));
        add(k.row().icon(Ic.ROUTER).title(a.s("settingsTab.network.server")).summary(state).trailing(buttons).build());

        value(Ic.SMARTPHONE, a.s("settingsTab.network.alias"), core.alias(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                aliasDialog();
            }
        });
        bool(Ic.NOTIFICATIONS, a.s("oldy.keepRunning"), a.s("oldy.keepRunningHint"), st.keepRunning(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setKeepRunning(v);
                ServerService.update(a);
            }
        });
        if (st.advancedSettings()) {
            final String[] types = {Device.TYPE_MOBILE, Device.TYPE_DESKTOP, Device.TYPE_WEB, Device.TYPE_HEADLESS, Device.TYPE_SERVER};
            final int ct = indexOf(types, st.deviceType());
            value(UiUtil.deviceIcon(st.deviceType()), a.s("settingsTab.network.deviceType"), types[ct], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    choose(a.s("settingsTab.network.deviceType"), types, ct, new Choice() {
                        @Override
                        public void chosen(int index) {
                            st.setDeviceType(types[index]);
                            rebuild();
                        }
                    });
                }
            });
            value(Ic.DEVICES, a.s("settingsTab.network.deviceModel"), st.deviceModel(), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askText(a.s("settingsTab.network.deviceModel"), st.deviceModel(), InputType.TYPE_CLASS_TEXT, new TextResult() {
                        @Override
                        public void done(String text) {
                            st.setDeviceModel(text);
                            rebuild();
                        }
                    });
                }
            });
            value(Ic.TUNE, a.s("settingsTab.network.port"), String.valueOf(st.port())
                    + (st.port() != Settings.DEFAULT_PORT ? "\n" + a.s("settingsTab.network.portWarning", "defaultPort", Settings.DEFAULT_PORT) : ""), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askText(a.s("settingsTab.network.port"), String.valueOf(st.port()), InputType.TYPE_CLASS_NUMBER, new TextResult() {
                        @Override
                        public void done(String text) {
                            try {
                                int p = Integer.parseInt(text.trim());
                                if (p > 0 && p < 65536) {
                                    st.setPort(p);
                                    k.toast(a.s("settingsTab.network.needRestart"));
                                }
                            } catch (NumberFormatException ignored) {
                            }
                            rebuild();
                        }
                    });
                }
            });
            value(Ic.ROUTER, a.s("settingsTab.network.network"),
                    st.networkWhitelist().isEmpty() && st.networkBlacklist().isEmpty() ? a.s("settingsTab.network.networkOptions.all")
                            : a.s("settingsTab.network.networkOptions.filtered"), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            a.open(NetworkInterfacesActivity.class);
                        }
                    });
            value(Ic.TIMER, a.s("settingsTab.network.discoveryTimeout"), st.discoveryTimeout() + " ms", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askText(a.s("settingsTab.network.discoveryTimeout"), String.valueOf(st.discoveryTimeout()), InputType.TYPE_CLASS_NUMBER, new TextResult() {
                        @Override
                        public void done(String text) {
                            try {
                                st.setDiscoveryTimeout(Math.max(100, Integer.parseInt(text.trim())));
                            } catch (NumberFormatException ignored) {
                            }
                            rebuild();
                        }
                    });
                }
            });
            if (Settings.httpsSupported()) {
                bool(Ic.LOCK, a.s("settingsTab.network.encryption"), null, st.encryption(), new BoolSetter() {
                    @Override
                    public void set(boolean v) {
                        st.setEncryption(v);
                        if (!v) {
                            k.dialog().title(a.s("dialogs.encryptionDisabledNotice.title")).message(a.s("dialogs.encryptionDisabledNotice.content"))
                                    .positive(a.s("general.close"), null).show();
                        }
                        a.core.restartServer();
                    }
                });
            } else {
                add(k.row().icon(Ic.LOCK_OPEN).title(a.s("settingsTab.network.encryption")).summary(a.s("general.off") + " — " + a.s("oldy.httpsUnavailable"))
                        .summaryLines(6).build());
            }
            value(Ic.WIFI, a.s("settingsTab.network.multicastGroup"), st.multicastGroup()
                    + (!Settings.DEFAULT_MULTICAST.equals(st.multicastGroup()) ? "\n" + a.s("settingsTab.network.multicastGroupWarning", "defaultMulticast", Settings.DEFAULT_MULTICAST) : ""),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            askText(a.s("settingsTab.network.multicastGroup"), st.multicastGroup(), InputType.TYPE_CLASS_TEXT, new TextResult() {
                                @Override
                                public void done(String text) {
                                    if (com.disgusty.oldysend.net.NetUtil.isValidIpv4(text.trim())) {
                                        st.setMulticastGroup(text.trim());
                                        k.toast(a.s("settingsTab.network.needRestart"));
                                    }
                                    rebuild();
                                }
                            });
                        }
                    });
            value(Ic.SECURITY, a.s("oldy.resetIdentity"), core.fingerprint().length() > 24 ? core.fingerprint().substring(0, 24) + "…" : core.fingerprint(),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            k.dialog().title(a.s("oldy.resetIdentity")).message(a.s("oldy.resetIdentityHint"))
                                    .negative(a.s("general.cancel"), null)
                                    .positive(a.s("general.confirm"), new Runnable() {
                                        @Override
                                        public void run() {
                                            a.core.resetIdentity();
                                        }
                                    }).show();
                        }
                    });
        }
    }

    private void aliasDialog() {
        final Field f = k.field(a.s("settingsTab.network.alias"), false);
        f.text(a.core.alias());
        LinearLayout box = k.vbox();
        box.addView(f.view, Kit.matchWrap());
        LinearLayout buttons = k.hbox();
        buttons.addView(k.button(a.s("settingsTab.network.generateRandomAlias"), Kit.B_TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                f.text(Core.randomAlias());
            }
        }));
        buttons.addView(k.button(a.s("settingsTab.network.useSystemName"), Kit.B_TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                f.text(Settings.defaultDeviceModel() + " " + android.os.Build.MODEL);
            }
        }));
        box.addView(buttons);
        k.dialog().title(a.s("settingsTab.network.alias")).view(box).negative(a.s("general.cancel"), null)
                .positive(a.s("general.save"), new Runnable() {
                    @Override
                    public void run() {
                        String v = f.text().trim();
                        if (v.length() > 0) {
                            st.setAlias(v);
                            a.core.discovery.announce();
                        }
                        rebuild();
                    }
                }).show();
    }

    // ---------------------------------------------------------------- other

    private void other() {
        section(a.s("settingsTab.other.title"));
        value(Ic.INFO, a.s("aboutPage.title"), "OldySend " + com.disgusty.oldysend.BuildConfig.VERSION_NAME, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.open(AboutActivity.class);
            }
        });
        value(Ic.HELP, a.s("troubleshootPage.title"), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.open(TroubleshootActivity.class);
            }
        });
        value(Ic.OPEN_IN_NEW, a.s("settingsTab.other.privacyPolicy"), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl("https://localsend.org/privacy");
            }
        });
        value(Ic.BUG_REPORT, a.s("oldy.debug"), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.open(DebugActivity.class);
            }
        });
        bool(Ic.TUNE, a.s("settingsTab.advancedSettings"), null, st.advancedSettings(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setAdvancedSettings(v);
                rebuild();
            }
        });
    }

    private void openUrl(String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            k.toast(url);
        }
    }

    // ---------------------------------------------------------------- utils

    private interface TextResult {
        void done(String text);
    }

    private void askText(String title, String initial, int inputType, final TextResult r) {
        final Field f = k.field(null, false);
        Compat.inputType(f.edit, inputType);
        f.text(Text.orEmpty(initial));
        k.dialog().title(title).view(f.view).negative(a.s("general.cancel"), null).positive(a.s("general.confirm"), new Runnable() {
            @Override
            public void run() {
                r.done(f.text());
            }
        }).show();
        f.edit.requestFocus();
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    @TargetApi(21)
    private static final class Api21 {
        static void persist(Activity a, Uri uri) {
            try {
                a.getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }
    }
}
