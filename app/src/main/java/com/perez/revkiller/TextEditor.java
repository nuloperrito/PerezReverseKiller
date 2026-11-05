package com.perez.revkiller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.perez.res.ARSCEditor;
import com.perez.res.AXmlEditor;
import com.perez.util.FileUtil;
import com.perez.util.RealFuncUtil;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;

public class TextEditor extends AppCompatActivity {

    public static final int SETTEXT = 1;
    public static final int TOAST = 2;
    public static final String PLUGIN = "plugin";
    public static final String DATA = "data";

    Edit edit;
    EditText text;
    private SharedPreferences mPreferences;
    private TextSettings mSettings;
    private boolean isViewText = true;
    private boolean isChanged = false;
    private boolean noText = false;

    public static String searchString = "";
    public static String replaceString = "";
    private ScrollView scroll;
    private String mFilePath;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case SETTEXT:
                text.setText(msg.obj != null ? msg.obj.toString() : "");
                isChanged = false;
                break;
            case TOAST:
                toast(msg.obj != null ? msg.obj.toString() : "");
                break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            handlerIntent();
            if(isViewText)
                setContentView(R.layout.view_text);
            else
                setContentView(R.layout.text_editor);
            text = findViewById(R.id.txtEdit);
            scroll = findViewById(R.id.scroll);
            mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            mSettings = new TextSettings(mPreferences);
            updatePrefs();
            if(mFilePath != null) {
                setTitle(new File(mFilePath).getName());
            }
            open();
            TextWatcher watch = new TextWatcher() {
                public void beforeTextChanged(CharSequence c, int start, int count, int after) {}
                public void onTextChanged(CharSequence c, int start, int count, int after) {}
                public void afterTextChanged(Editable edit) {
                    if(!isChanged)
                        isChanged = true;
                }
            };
            text.addTextChangedListener(watch);
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (!noText && isChanged) {
                        showDialog();
                        return;
                    }
                    finish();
                }
            });
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void showDialog() {
        RealFuncUtil.showDlgMsg(this, getString(R.string.prompt),
                               getString(R.string.is_save),
                (dialog, which) -> {
                    if(which == AlertDialog.BUTTON_POSITIVE) {
                        if(write()) {
                            result();
                        }
                    } else if(which == AlertDialog.BUTTON_NEGATIVE) {
                        finish();
                    }
                });
    }

    public void open() {
        if(mFilePath == null || mFilePath.isEmpty()) {
            noText = true;
            return;
        }
        new Thread(() -> {
            try {
                File file = new File(mFilePath);
                if(!file.exists() || !file.canRead()) {
                    throw new IOException("File not found or unreadable: " + mFilePath);
                }
                byte[] fileBytes = FileUtil.readFile(file);
                List<String> list = new ArrayList<>();
                edit.read(list, fileBytes);
                Message msg = new Message();
                msg.what = SETTEXT;
                msg.obj = RealFuncUtil.joinStr(list, "\n");
                mHandler.sendMessage(msg);
            } catch(Exception e) {
                noText = true;
                Message msg = new Message();
                msg.what = TOAST;
                msg.obj = e.getMessage() != null ? e.getMessage() : "Error reading file";
                mHandler.sendMessage(msg);
            }
        }).start();
    }

    private void updatePrefs() {
        mSettings.readPrefs(mPreferences);
        text.setHorizontallyScrolling(!mSettings.mLineWrap);
        String font = mSettings.mFontType;
        if(font.equals("Serif"))
            text.setTypeface(Typeface.SERIF);
        else if(font.equals("Sans Serif"))
            text.setTypeface(Typeface.SANS_SERIF);
        else
            text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(mSettings.mFontSize);
        text.setTextColor(mSettings.mFontColor);
        text.setBackgroundColor(mSettings.mBgColor);
        scroll.setBackgroundColor(mSettings.mBgColor);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePrefs();
    }

    private void searchString() {
        LayoutInflater inflate = getLayoutInflater();
        ScrollView scroll = (ScrollView) inflate.inflate(
                                R.layout.alert_dialog_search_or_replace, null);
        final CheckBox from_start = scroll.findViewById(R.id.from_start);
        final EditText srcName = scroll.findViewById(R.id.src_edit);
        final CheckBox isReplace = scroll.findViewById(R.id.replace);
        final EditText dstName = scroll.findViewById(R.id.replace_edit);
        srcName.setText(searchString);
        dstName.setText(replaceString);
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.search_string);
        alert.setView(scroll);
        alert.setPositiveButton(android.R.string.ok,
        (dialog, whichButton) -> {
            searchString = srcName.getText().toString();
            replaceString = dstName.getText().toString();
            if(searchString.isEmpty()) {
                toast(getString(R.string.search_name_empty));
                return;
            }
            int index = from_start.isChecked() ? 0 : text.getSelectionStart();
            if(isReplace.isChecked()) {
                if(!replace(searchString, replaceString, index)) {
                    toast(String.format(getString(R.string.search_not_found), searchString));
                }
                return;
            }
            if(!searchString(searchString, index)) {
                toast(String.format(getString(R.string.search_not_found), searchString));
            }
        });
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

    private boolean searchString(String src, int index) {
        CharSequence seq = text.getText();
        index = seq.toString().indexOf(src, index + 1);
        if(index != -1) {
            text.setSelection(index, index + src.length());
            return true;
        }
        return false;
    }

    private boolean replace(String src, String dst, int index) {
        Editable editable = text.getEditableText();
        String s = text.getText().toString();
        if((index = s.indexOf(src, index + 1)) != -1) {
            editable.replace(index, index + src.length(), dst);
            text.setSelection(index, index + dst.length());
            return true;
        }
        return false;
    }

    private void handlerIntent() {
        Intent intent = getIntent();
        String plugin = intent.getStringExtra(PLUGIN);
        load(plugin);

        mFilePath = intent.getStringExtra("filePath");
        if(mFilePath == null && intent.getData() != null) {
            mFilePath = intent.getData().getPath();
        }
    }

    private void load(String name) {
        if("ARSCEditor".equals(name)) {
            isViewText = false;
            edit = new ARSCEditor();
        } else if("TextEditor".equals(name)) {
            isViewText = true;
            edit = new Text();
        } else if("AXmlEditor".equals(name)) {
            isViewText = false;
            edit = new AXmlEditor();
        } else if("StringIdsEditor".equals(name)) {
            isViewText = false;
            edit = new StringIdsEditor();
        } else if("TypeIdsEditor".equals(name)) {
            isViewText = false;
            edit = new TypeIdsEditor();
        }
    }

    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private boolean write() {
        if(mFilePath == null || mFilePath.isEmpty()) {
            return false;
        }
        File targetFile = new File(mFilePath);
        File bakFile = new File(targetFile.getParentFile(), targetFile.getName() + ".bak");
        try {
            if(targetFile.exists()) {
                FileUtil.copyFile(targetFile, bakFile);
            }
        } catch(Exception ignored) {
        }

        try (FileOutputStream out = new FileOutputStream(targetFile)) {
            String data = text.getText().toString();
            edit.write(data, out);
            out.flush();
            isChanged = false;
            return true;
        } catch(IOException e) {
            e.printStackTrace();
            toast(getString(R.string.failure) + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        MenuInflater in = getMenuInflater();
        in.inflate(R.menu.text_editor_menu, m);
        if(noText)
            m.removeItem(R.id.save);
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        edit = null;
        scroll = null;
        mSettings = null;
        mPreferences = null;
        System.gc();
    }

    private void result() {
        Intent intent = getIntent();
        setResult(ActResConstant.text_editor, intent);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        switch(mi.getItemId()) {
        case R.id.save:
            if(write()) {
                toast(getString(R.string.saved));
                result();
            }
            break;
        case R.id.exit:
            if(noText) {
                finish();
                return true;
            }
            if(isChanged)
                showDialog();
            else
                finish();
            break;
        case R.id.search_string:
            searchString();
            break;
        case R.id.preferences: {
            Intent intent = new Intent(this, TextPreferences.class);
            startActivity(intent);
            break;
        }
        }
        return true;
    }

    class Text implements Edit {
        @Override
        public void read(List<String> data, byte[] input) throws IOException {
            if(input == null) return;
            String s = new String(input, "UTF-8");
            String[] strs = s.split("\n");
            for(String str : strs)
                data.add(str);
        }

        @Override
        public void write(String data, OutputStream out) throws IOException {
            if(data != null) {
                out.write(data.getBytes("UTF-8"));
            }
        }
    }
}