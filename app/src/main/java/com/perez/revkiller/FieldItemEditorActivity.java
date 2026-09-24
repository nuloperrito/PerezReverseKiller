package com.perez.revkiller;

import android.os.Bundle;
import android.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.EditText;
import android.view.KeyEvent;
import android.text.TextWatcher;
import android.text.Editable;

import com.perez.util.RealFuncUtil;

import java.util.regex.*;

import org.jb.dexlib.*;
import org.jb.dexlib.Util.*;
import org.jb.dexlib.ClassDataItem.*;

public class FieldItemEditorActivity extends AppCompatActivity {
    public static final Pattern pattern = Pattern.compile("\\s");
    private boolean isChanged;
    private EditText accessFlagsEdit;
    private EditText fieldNameEdit;
    private EditText descriptorEdit;
    private EncodedField field;
    private ClassDefItem classDef;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.field_item_editor);
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
        fieldNameEdit = findViewById(R.id.field_name_edit);
        fieldNameEdit.addTextChangedListener(watch);
        descriptorEdit = findViewById(R.id.field_descriptor_edit);
        descriptorEdit.addTextChangedListener(watch);
        init();
    }

    private void init() {
        classDef = ClassListActivity.curClassDef;
        if(FieldListActivity.isStaticField)
            field = classDef.getClassData().getStaticFields()[FieldListActivity.fieldIndex];
        else
            field = classDef.getClassData().getInstanceFields()[FieldListActivity.fieldIndex];
        accessFlagsEdit.setText(AccessFlags
                                .formatAccessFlagsForField(field.accessFlags));
        fieldNameEdit.setText(field.field.getFieldName().getStringValue());
        descriptorEdit.setText(field.field.getFieldType().getTypeDescriptor());
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
                                if(save(ClassListActivity.dexFile)) {
                                    setResult(ActResConstant.field_item_editor);
                                    finish();
                                }
                            } else if(which == AlertDialog.BUTTON_NEGATIVE) {
                                ClassListActivity.isChanged = false;
                                finish();
                            }
                        });
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean save(DexFile dexFile) {
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
        } catch(Exception e) {
            RealFuncUtil.showDlgMsg(this, getString(R.string.accflag_error), RealFuncUtil.getFullException(e), null);
            return false;
        }
        try {
            FieldIdItem field = FieldIdItem.internFieldIdItem(dexFile, classDef
                                .getClassType(), TypeIdItem.internTypeIdItem(dexFile,
                                        descriptorEdit.getText().toString()), StringIdItem
                                .internStringIdItem(dexFile, fieldNameEdit.getText()
                                                    .toString()));
            if(FieldListActivity.isStaticField) {
                classDef.getClassData().setStaticField(
                    FieldListActivity.fieldIndex,
                    new EncodedField(field, accessFlags));
            } else {
                classDef.getClassData().setInstanceField(
                    FieldListActivity.fieldIndex,
                    new EncodedField(field, accessFlags));
            }
            ClassListActivity.isChanged = true;
            isChanged = false;
        } catch (Exception e) {
            RealFuncUtil.showDlgMsg(this, getString(R.string.field_desc_error), RealFuncUtil.getFullException(e), null);
            return false;
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clearAll();
    }

    private void clearAll() {
        classDef = null;
        accessFlagsEdit = null;
        fieldNameEdit = null;
        descriptorEdit = null;
        System.gc();
    }
}
