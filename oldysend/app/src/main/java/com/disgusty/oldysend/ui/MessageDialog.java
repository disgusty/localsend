package com.disgusty.oldysend.ui;

import android.widget.LinearLayout;

import com.disgusty.oldysend.ui.kit.Field;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.ui.kit.Toggle;

/** "Type message" dialog with the multiline switch. */
final class MessageDialog {
    interface Listener {
        void onMessage(String text);
    }

    private MessageDialog() {
    }

    static void show(final BaseActivity a, final Listener l) {
        final Kit k = a.k;
        final boolean multi = a.core.settings.multilineMessages();
        final Field f = k.field(null, multi);
        LinearLayout box = k.vbox();
        box.addView(f.view, Kit.matchWrap());
        LinearLayout row = k.hbox();
        row.setPadding(0, k.dp(8), 0, 0);
        row.addView(k.text(Kit.T_BODY, a.s("dialogs.messageInput.multiline")), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        final Toggle tg = k.toggle(Kit.SWITCH);
        tg.setChecked(multi);
        row.addView(tg);
        box.addView(row, Kit.matchWrap());
        final com.disgusty.oldysend.ui.kit.DialogBuilder[] d = new com.disgusty.oldysend.ui.kit.DialogBuilder[1];
        tg.listener(new Toggle.Listener() {
            @Override
            public void onChanged(Toggle toggle, boolean checked) {
                a.core.settings.setMultilineMessages(checked);
                f.edit.setSingleLine(!checked);
                if (checked) {
                    com.disgusty.oldysend.ui.Compat.inputType(f.edit, android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    f.edit.setMinLines(3);
                    f.edit.setMaxLines(8);
                } else {
                    f.edit.setMinLines(1);
                }
            }
        });
        d[0] = k.dialog().title(a.s("dialogs.messageInput.title")).view(box)
                .negative(a.s("general.cancel"), null)
                .positive(a.s("general.confirm"), new Runnable() {
                    @Override
                    public void run() {
                        String text = f.text();
                        if (text.trim().length() > 0) l.onMessage(text);
                    }
                });
        d[0].show();
        f.edit.requestFocus();
    }

}
