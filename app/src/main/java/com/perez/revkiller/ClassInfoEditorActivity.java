package com.perez.revkiller;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import android.view.KeyEvent;
import android.text.TextWatcher;
import android.text.Editable;

import java.util.regex.*;
import java.util.*;

import org.jb.dexlib.*;
import org.jb.dexlib.Util.*;
import androidx.appcompat.app.AppCompatActivity;

import com.perez.util.RealFuncUtil;

public class ClassInfoEditorActivity extends AppCompatActivity {
    public static final Pattern pattern = Pattern.compile("\\s");
    private EditText accessFlagsEdit;
    private EditText superclassEdit;
    private EditText interfacesEdit;
    private EditText sourceFileEdit;
    private boolean isChanged;
    private ClassDefItem classDef;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.class_info_editor);
        TextWatcher watch = new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int start, int count,
                                          int after) {
            }
            public void onTextChanged(CharSequence c, int start, int count,
                                      int after) {
            }
            public void afterTextChanged(Editable edit) {
                if(!isChanged)
                    isChanged = true;
            }
        };
        accessFlagsEdit = findViewById(R.id.access_flags_edit);
        accessFlagsEdit.addTextChangedListener(watch);
        superclassEdit = findViewById(R.id.super_class_edit);
        superclassEdit.addTextChangedListener(watch);
        interfacesEdit = findViewById(R.id.interface_edit);
        interfacesEdit.addTextChangedListener(watch);
        sourceFileEdit = findViewById(R.id.source_file_edit);
        sourceFileEdit.addTextChangedListener(watch);
        init();
    }

    private void init() {
        classDef = ClassListActivity.curClassDef;
        accessFlagsEdit.setText(AccessFlags.formatAccessFlagsForClass(classDef
                                .getAccessFlags()));
        String superClassName = classDef.getSuperclass().getTypeDescriptor();
        superclassEdit.setText(superClassName);

        String interfaces = classDef.getInterfaces() != null ? classDef
                            .getInterfaces().getTypeListString(" ") : "";
        interfacesEdit.setText(interfaces);
        String source = classDef.getSourceFile() != null ? classDef
                        .getSourceFile().getStringValue() : "";
        sourceFileEdit.setText(source);
        isChanged = false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            if(isChanged) {
                RealFuncUtil.showDlgMsg(this, getString(R.string.prompt),
                                       getString(R.string.is_save),
                        (dialog, which) -> {
                            if(which == AlertDialog.BUTTON_POSITIVE) {
                                save(ClassListActivity.dexFile);
                                finish();
                            } else if(which == AlertDialog.BUTTON_NEGATIVE)
                                finish();
                        });
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void save(DexFile dexFile) {
        String[] str;
        int accessFlags = 0;
        try {
            String ac = accessFlagsEdit.getText().toString();
            if(!ac.isEmpty()) {
                str = pattern.split(accessFlagsEdit.getText().toString());
                for (String s : str) {
                    AccessFlags accessFlag = AccessFlags.getAccessFlag(s);
                    accessFlags |= accessFlag.getValue();
                }
            }
            classDef.accessFlags = accessFlags;
        } catch(Exception e) {
            RealFuncUtil.showDlgMsg(this, getString(R.string.accflag_error), RealFuncUtil.getFullException(e), null);
        }

        classDef.superType = TypeIdItem.internTypeIdItem(dexFile,
                             superclassEdit.getText().toString());

        ArrayList<TypeIdItem> types = new ArrayList<TypeIdItem>();
        String in = interfacesEdit.getText().toString();
        if(!in.isEmpty()) {
            str = pattern.split(in);
            for (String s : str) {
                if (s.isEmpty())
                    continue;
                types.add(TypeIdItem.internTypeIdItem(dexFile, s));
            }
        }
        TypeListItem typeList = null;
        if(!types.isEmpty())
            typeList = TypeListItem.internTypeListItem(dexFile, types);
        classDef.implementedInterfaces = typeList;
        String sourceFile = sourceFileEdit.getText().toString().trim();
        if(!sourceFile.isEmpty()) {
            classDef.sourceFile = StringIdItem.internStringIdItem(dexFile,
                                  sourceFile);
        } else
            classDef.sourceFile = null;
        ClassListActivity.isChanged = true;
        isChanged = false;
    }

    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void clearAll() {
        classDef = null;
        accessFlagsEdit = null;
        superclassEdit = null;
        interfacesEdit = null;
        sourceFileEdit = null;
        System.gc();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clearAll();
    }
}
