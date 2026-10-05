package com.disgusty.oldysend.ui;

import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.net.NetUtil;
import com.disgusty.oldysend.ui.kit.Field;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.ui.kit.Segmented;

import java.util.ArrayList;
import java.util.List;

/** Manual sending: hashtag (last octet in the local subnet) or full IP address. */
final class AddressDialog {
    interface Listener {
        void onAddress(String ip);
    }

    private AddressDialog() {
    }

    static void show(final BaseActivity a, final Listener l) {
        final Kit k = a.k;
        final int[] mode = {0};
        LinearLayout box = k.vbox();
        final Segmented seg = k.segmented(new String[]{a.s("dialogs.addressInput.hashtag"), a.s("dialogs.addressInput.ip")}, null);
        seg.select(0);
        box.addView(seg.view(), Kit.matchWrap());
        box.addView(k.space(12));
        final Field f = k.field(null, false);
        com.disgusty.oldysend.ui.Compat.inputType(f.edit, InputType.TYPE_CLASS_NUMBER);
        f.edit.setHint("123");
        box.addView(f.view, Kit.matchWrap());
        final List<NetUtil.LocalAddress> locals = a.core.localAddresses();
        final TextView example = k.text(Kit.T_CAPTION, "");
        example.setPadding(0, k.dp(8), 0, 0);
        box.addView(example, Kit.matchWrap());
        final String[] recent = recent(a);
        if (recent.length > 0) {
            TextView rl = k.text(Kit.T_CAPTION, a.s("dialogs.addressInput.recentlyUsed"));
            rl.setPadding(0, k.dp(8), 0, 0);
            box.addView(rl);
            for (final String r : recent) {
                View b = k.button(r, Kit.B_TEXT, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        seg.select(1);
                        mode[0] = 1;
                        com.disgusty.oldysend.ui.Compat.inputType(f.edit, InputType.TYPE_CLASS_TEXT);
                        f.text(r);
                    }
                });
                box.addView(b);
            }
        }
        final Runnable updateExample = new Runnable() {
            @Override
            public void run() {
                String prefix = locals.isEmpty() ? "192.168.1." : locals.get(0).subnetPrefix();
                example.setText(a.s("general.example") + ": " + (mode[0] == 0 ? "123 → " + prefix + "123" : prefix + "123"));
            }
        };
        updateExample.run();
        seg.listener(new Segmented.Listener() {
            @Override
            public void onSelected(int index) {
                mode[0] = index;
                com.disgusty.oldysend.ui.Compat.inputType(f.edit, index == 0 ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
                f.edit.setHint(index == 0 ? "123" : "192.168.1.123");
                updateExample.run();
            }
        });
        final com.disgusty.oldysend.ui.kit.DialogBuilder d = k.dialog().title(a.s("dialogs.addressInput.title")).view(box).keepOpen();
        d.negative(a.s("general.cancel"), new Runnable() {
            @Override
            public void run() {
                d.dismiss();
            }
        }).positive(a.s("general.confirm"), new Runnable() {
            @Override
            public void run() {
                String input = f.text().trim();
                String ip;
                if (mode[0] == 0) {
                    String digits = input.startsWith("#") ? input.substring(1) : input;
                    String prefix = locals.isEmpty() ? null : locals.get(0).subnetPrefix();
                    ip = prefix == null ? null : prefix + digits;
                } else {
                    ip = input;
                }
                if (ip == null || !NetUtil.isValidIpv4(ip)) {
                    f.setError(a.s("general.error"));
                    return;
                }
                remember(a, ip);
                d.dismiss();
                l.onAddress(ip);
            }
        });
        d.show();
        f.edit.requestFocus();
    }

    private static String[] recent(BaseActivity a) {
        String v = a.core.settings.recentAddresses();
        List<String> out = new ArrayList<String>();
        for (String s : v.split(",")) if (s.trim().length() > 0) out.add(s.trim());
        return out.toArray(new String[out.size()]);
    }

    private static void remember(BaseActivity a, String ip) {
        List<String> list = new ArrayList<String>();
        list.add(ip);
        for (String s : recent(a)) if (!s.equals(ip) && list.size() < 3) list.add(s);
        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(sb.length() > 0 ? "," : "").append(s);
        a.core.settings.setRecentAddresses(sb.toString());
    }
}
