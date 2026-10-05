package com.disgusty.oldysend.ui;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.oldysend.ui.icon.IconDrawable;
import com.disgusty.oldysend.ui.kit.Kit;
import com.disgusty.oldysend.ui.kit.Segmented;
import com.disgusty.oldysend.util.Codec;
import com.disgusty.oldysend.util.IO;
import com.disgusty.oldysend.util.Log;
import com.disgusty.oldysend.util.Text;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Compares both devices' fingerprints (LocalSend's VerifyPage): the sorted, joined fingerprints are hashed and the
 * first 128 bits are shown as 16 icons of LocalSend's icon alphabet (assets/verify_icons.txt, same order as
 * app/lib/util/fingerprint_alphabet.dart), or as text.
 */
public final class VerifyActivity extends BaseActivity {
    private static final String EXTRA_FINGERPRINT = "fingerprint";
    private static String[] alphabet;

    private String combined;
    private LinearLayout body;
    private int mode;

    static void open(BaseActivity a, String peerFingerprint) {
        a.startActivity(new Intent(a, VerifyActivity.class).putExtra(EXTRA_FINGERPRINT, peerFingerprint));
    }

    @Override
    protected void build() {
        screen.appBar.title(s("verifyPage.title")).up(finisher());
        String peer = getIntent().getStringExtra(EXTRA_FINGERPRINT);
        combined = combine(core.fingerprint(), peer == null ? "" : peer);

        LinearLayout col = k.vbox();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(k.pagePadding(), k.dp(20), k.pagePadding(), k.dp(20));
        Segmented seg = k.segmented(new String[]{s("verifyPage.icons"), s("verifyPage.text")}, null);
        seg.select(mode);
        seg.listener(new Segmented.Listener() {
            @Override
            public void onSelected(int index) {
                mode = index;
                showBody();
            }
        });
        // Segment buttons share the width by weight, so the group needs a definite width.
        col.addView(seg.view(), new LinearLayout.LayoutParams(k.dp(240), LinearLayout.LayoutParams.WRAP_CONTENT));
        body = k.vbox();
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(body, k.margins(Kit.matchWrap(), 0, 20, 0, 20));
        TextView q = k.text(Kit.T_SECONDARY, s("verifyPage.question"));
        q.setGravity(Gravity.CENTER);
        col.addView(q, Kit.matchWrap());
        showBody();
        screen.setContent(k.scroll(col));
    }

    private void showBody() {
        body.removeAllViews();
        if (mode == 1) {
            TextView tv = k.text(Kit.T_BODY, combined);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setGravity(Gravity.CENTER);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ReceiveActivity.copy(VerifyActivity.this, combined);
                    k.toast(s("general.copiedToClipboard"));
                }
            });
            body.addView(tv, Kit.matchWrap());
            return;
        }
        String[] icons = alphabet(this);
        int[] idx = toAlphabet(combined, icons.length);
        int color = k.textColor(Kit.T_BODY);
        LinearLayout row = null;
        for (int i = 0; i < idx.length; i++) {
            if (i % 4 == 0) {
                row = k.hbox();
                body.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            ImageView iv = k.image(new IconDrawable(icons[idx[i]], k.dp(32), color));
            row.addView(iv, k.margins(new LinearLayout.LayoutParams(k.dp(32), k.dp(32)), 5, 5, 5, 5));
        }
    }

    /** Both fingerprints sorted and joined, as on the other device. */
    static String combine(String mine, String peer) {
        String[] f = {mine, peer};
        Arrays.sort(f);
        return f[0] + f[1];
    }

    /** SHA-256 of the combined text, first 16 bytes, written in base {@code base} with a fixed number of digits. */
    static int[] toAlphabet(String combined, int base) {
        byte[] digest = Codec.sha256(Text.utf8(combined));
        byte[] first = new byte[17];
        System.arraycopy(digest, 0, first, 1, 16); // leading zero byte keeps the value positive
        BigInteger value = new BigInteger(first);
        int length = (int) Math.ceil(128 * Math.log(2) / Math.log(base));
        int[] out = new int[length];
        BigInteger b = BigInteger.valueOf(base);
        for (int i = length - 1; i >= 0; i--) {
            BigInteger[] qr = value.divideAndRemainder(b);
            out[i] = qr[1].intValue();
            value = qr[0];
        }
        return out;
    }

    static synchronized String[] alphabet(Context c) {
        if (alphabet != null) return alphabet;
        List<String> lines = new ArrayList<String>();
        InputStream in = null;
        try {
            in = c.getAssets().open("verify_icons.txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"), 8192);
            String line;
            while ((line = r.readLine()) != null) if (line.length() > 0) lines.add(line);
        } catch (Exception e) {
            Log.e("Could not read verify icons", e);
        } finally {
            IO.close(in);
        }
        alphabet = lines.toArray(new String[lines.size()]);
        return alphabet;
    }
}
