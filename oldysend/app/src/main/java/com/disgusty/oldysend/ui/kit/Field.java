package com.disgusty.oldysend.ui.kit;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

/** A text input with its style-specific decoration (label, underline, outline...). */
public class Field {
    public final View view;
    public final EditText edit;
    protected final TextView error;

    public Field(View view, EditText edit, TextView error) {
        this.view = view;
        this.edit = edit;
        this.error = error;
    }

    public String text() {
        return edit.getText().toString();
    }

    public Field text(CharSequence s) {
        edit.setText(s);
        edit.setSelection(edit.getText().length());
        return this;
    }

    public void setError(String message) {
        if (error == null) return;
        error.setText(message == null ? "" : message);
        error.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }
}
