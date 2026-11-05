package com.perez.revkiller;

import androidx.appcompat.widget.AppCompatEditText;

import android.view.inputmethod.InputContentInfo;
import android.os.Bundle;
import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.text.ClipboardManager;
import android.text.Selection;
import android.text.NoCopySpan;
import android.text.Editable;
import android.text.method.TransformationMethod;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;

import org.jb.dexlib.Util.Utf8Utils;

public class PerezEditText extends AppCompatEditText {
    public static final int INSERT = 0;
    public static final int NORMAL = 1;
    public static final int VISUAL = 2;

    private int mod = NORMAL;

    private static final int ID_SELECT_ALL = android.R.id.selectAll;
    private static final int ID_START_SELECTING_TEXT = android.R.id.startSelectingText;
    private static final int ID_STOP_SELECTING_TEXT = android.R.id.stopSelectingText;
    private static final int ID_CUT = android.R.id.cut;
    private static final int ID_COPY = android.R.id.copy;
    private static final int ID_PASTE = android.R.id.paste;
    private static final int ID_COPY_URL = android.R.id.copyUrl;
    private static final int ID_SWITCH_INPUT_METHOD = android.R.id.switchInputMethod;
    private static final int ID_ADD_TO_DICTIONARY = android.R.id.addToDictionary;

    public PerezEditText(Context context) {
        this(context, null);
    }

    public PerezEditText(Context context, AttributeSet attr) {
        super(context, attr);
    }

    public PerezEditText(Context context, AttributeSet attr, int a) {
        super(context, attr, a);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_DEL && handlerDel())
            return true;
        if(mod == NORMAL && handleKey(keyCode))
            return true;
        return super.onKeyDown(keyCode, event);
    }

    private boolean handlerDel() {
        CharSequence charSeq = getText();
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if(start == end && charSeq.length() != 0) {
            char c = charSeq.charAt(start > 0 ? start - 1 : start);
            if(c == '\n')
                return true;
        }
        CharSequence subSeq = charSeq.subSequence(Math.min(start, end),
                              Math.max(start, end));
        return subSeq.toString().indexOf('\n') != -1;
    }

    private boolean handleKey(int keyCode) {
        switch(keyCode) {
        case KeyEvent.KEYCODE_H:
            KeyEvent left = new KeyEvent(KeyEvent.ACTION_DOWN,
                                         KeyEvent.KEYCODE_DPAD_LEFT);
            super.onKeyDown(left.getKeyCode(), left);
            return true;
        case KeyEvent.KEYCODE_J:
        case KeyEvent.KEYCODE_ENTER:
            KeyEvent down = new KeyEvent(KeyEvent.ACTION_DOWN,
                                         KeyEvent.KEYCODE_DPAD_DOWN);
            super.onKeyDown(down.getKeyCode(), down);
            return true;
        case KeyEvent.KEYCODE_K:
            KeyEvent up = new KeyEvent(KeyEvent.ACTION_DOWN,
                                       KeyEvent.KEYCODE_DPAD_UP);
            super.onKeyDown(up.getKeyCode(), up);
            return true;
        case KeyEvent.KEYCODE_L:
            KeyEvent right = new KeyEvent(KeyEvent.ACTION_DOWN,
                                          KeyEvent.KEYCODE_DPAD_RIGHT);
            super.onKeyDown(right.getKeyCode(), right);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return super.onKeyUp(keyCode, event);
    }

    public void setMod(int mod) {
        this.mod = mod;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo edit) {
        final InputConnection target = super.onCreateInputConnection(edit);
        if (target == null) {
            return null;
        }

        return new android.view.inputmethod.InputConnectionWrapper(target, true) {
            @Override
            public boolean commitContent(InputContentInfo ic, int flags, Bundle opts) {
                return false;
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                return false;
            }

            @Override
            public boolean requestCursorUpdates(int p1) {
                return false;
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                CharSequence charSeq = getText();
                if (charSeq != null) {
                    int start = getSelectionStart();
                    int end = getSelectionEnd();
                    if (start != end) {
                        CharSequence subSeq = charSeq.subSequence(
                                Math.min(start, end), Math.max(start, end));
                        if (subSeq.toString().indexOf('\n') != -1) {
                            return true;
                        }
                    }
                }
                return super.commitText(text, newCursorPosition);
            }

            @Override
            public boolean deleteSurroundingText(int leftLength, int rightLength) {
                // Intercept IME backspace if it attempts to delete a newline
                if (handlerDel()) {
                    return true;
                }
                return super.deleteSurroundingText(leftLength, rightLength);
            }

            @Override
            public boolean setComposingRegion(int start, int end) {
                return false;
            }

            @Override
            public boolean commitCorrection(CorrectionInfo correctionInfo) {
                return false;
            }
        };
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        int selStart = getSelectionStart();
        int selEnd = getSelectionEnd();
        Editable text = getText();
        if(!isFocused()) {
            selStart = 0;
            selEnd = text.length();
        }
        int min = Math.min(selStart, selEnd);
        int max = Math.max(selStart, selEnd);
        if(min < 0)
            min = 0;
        if(max < 0)
            max = 0;
        ClipboardManager clip = (ClipboardManager) getContext()
                                .getSystemService(Context.CLIPBOARD_SERVICE);
        Object SELECTING = new NoCopySpan.Concrete();
        switch(id) {
        case ID_SELECT_ALL:
            super.onTextContextMenuItem(ID_SELECT_ALL);
            return true;
        case ID_START_SELECTING_TEXT:
            super.onTextContextMenuItem(ID_START_SELECTING_TEXT);
            return true;
        case ID_STOP_SELECTING_TEXT:
            super.onTextContextMenuItem(ID_STOP_SELECTING_TEXT);
            return true;
        case ID_CUT:
            text.removeSpan(SELECTING);
            if(min == max) {
                min = 0;
                max = text.length();
            }
            TransformationMethod transformation = getTransformationMethod();
            CharSequence transformed;
            if(transformation == null)
                transformed = text;
            else
                transformed = transformation.getTransformation(text, this);
            if(transformed.subSequence(min, max).toString().indexOf('\n') == -1) {
                clip.setText(transformed.subSequence(min, max));
                text.delete(min, max);
            }
            return true;
        case ID_COPY:
            super.onTextContextMenuItem(ID_COPY);
            return true;
        case ID_PASTE:
            text.removeSpan(SELECTING);
            CharSequence paste = clip.getText();
            if(paste != null) {
                Selection.setSelection(text, max);
                text.replace(min, max, Utf8Utils.escapeString(paste.toString()));
            }
            return true;
        case ID_COPY_URL:
            super.onTextContextMenuItem(ID_COPY_URL);
            return true;
        case ID_SWITCH_INPUT_METHOD:
            super.onTextContextMenuItem(ID_SWITCH_INPUT_METHOD);
            return true;
        case ID_ADD_TO_DICTIONARY:
            super.onTextContextMenuItem(ID_ADD_TO_DICTIONARY);
            return true;
        }
        return false;
    }

}
