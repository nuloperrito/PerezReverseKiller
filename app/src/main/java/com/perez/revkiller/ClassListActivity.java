package com.perez.revkiller;

import android.app.Dialog;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.content.Intent;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.ScrollView;
import android.widget.CheckBox;
import android.util.Log;
import android.database.DataSetObserver;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Stack;

import org.jb.dexlib.*;
import org.jb.dexlib.Util.*;

import com.perez.code.DalvikParser;
import com.perez.util.RealFuncUtil;

public class ClassListActivity extends AppCompatActivity {

    public static String searchString = "";
    public static String searchFieldClass = "";
    public static String searchFieldName = "";
    public static String searchFieldDescriptor = "";
    public static String searchMethodClass = "";
    public static String searchMethodName = "";
    public static String searchMethodDescriptor = "";
    public static final int SAVEFILE = 1;
    public static final int SAVEDISMISS = 2;
    private static final String title = "/";

    // Standard Java collections replacing the custom Tree class
    private String currentPath = "";
    private final Stack<String> pathStack = new Stack<>();

    public static HashMap<String, ClassDefItem> classMap;
    public static HashMap<String, ClassDefItem> deleteclassMap;
    public static DexFile dexFile;
    public static boolean isChanged;
    private static String dexFilePath = null;
    public static ClassDefItem curClassDef;

    public static String curFile;
    private ClassListAdapter mAdapter;
    private List<String> classList;

    private int mod;

    private static final int OPENDIR = 10;
    private static final int BACK = 11;
    private static final int UPDATE = 12;
    private static final int INIT = 13;
    private static final int TOAST = 14;
    private static final int SEARCH = 15;
    private static final int SEARCHDISMISS = 16;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case SAVEFILE:
                ClassListActivity.this.showDialog(SAVEFILE);
                break;
            case SEARCH:
                ClassListActivity.this.showDialog(SEARCH);
                break;
            case SAVEDISMISS:
                ClassListActivity.this.dismissDialog(SAVEFILE);
                break;
            case SEARCHDISMISS:
                ClassListActivity.this.dismissDialog(SEARCH);
                break;
            case TOAST:
                toast(msg.obj.toString());
                break;
            }
        }
    };

    public ListView lv;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.class_list);
        String path = getIntent().getStringExtra("filePath");
        if(path == null && getIntent().getData() != null) {
            path = getIntent().getData().getPath();
        }
        if(path != null) {
            dexFilePath = path;
            try {
                dexFile = new DexFile(dexFilePath);
            } catch(Exception e) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.open_dex_error);
                builder.setMessage(RealFuncUtil.getFullException(e));
                builder.setCancelable(false);
                builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                });
                builder.show();
                return;
            }
        } else {
            finish();
            return;
        }

        lv = findViewById(R.id.clslist);
        init();
        mAdapter = new ClassListAdapter(this);
        mAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onInvalidated() {
                switch(mod) {
                case OPENDIR:
                    pushPath(curFile);
                    classList = listCurrentLevel();
                    break;
                case BACK:
                    popPath();
                    classList = listCurrentLevel();
                    break;
                case UPDATE:
                    classList = listCurrentLevel();
                    break;
                case INIT:
                    init();
                    break;
                }
                setTitle(title + currentPath);
            }
        });
        lv.setAdapter(mAdapter);
        registerForContextMenu(lv);
        lv.setOnItemClickListener((list, view, position, id) -> {
            curFile = (String) list.getItemAtPosition(position);
            if(isDirectory(curFile)) {
                mod = OPENDIR;
                mAdapter.notifyDataSetInvalidated();
                return;
            }
            curClassDef = classMap.get(currentPath + curFile);
            Intent intent = new Intent(ClassListActivity.this, ClassItemActivity.class);
            startActivity(intent);
        });
        Button btn = findViewById(R.id.btn_string_pool);
        btn.setOnClickListener(v -> openStringPool());
    }

    private boolean isDirectory(String name) {
        return name != null && name.endsWith("/");
    }

    private void pushPath(String dirName) {
        pathStack.push(dirName);
        currentPath = currentPath + dirName;
    }

    private void popPath() {
        if(!pathStack.isEmpty()) {
            pathStack.pop();
            StringBuilder sb = new StringBuilder();
            for(String segment : pathStack) {
                sb.append(segment);
            }
            currentPath = sb.toString();
        }
    }

    private List<String> listCurrentLevel() {
        Set<String> entries = new HashSet<>();
        if(classMap != null) {
            for(String className : classMap.keySet()) {
                if(deleteclassMap != null && deleteclassMap.containsKey(className)) {
                    continue;
                }
                if(className.startsWith(currentPath)) {
                    String remainder = className.substring(currentPath.length());
                    if(remainder.isEmpty()) {
                        continue;
                    }
                    int slashIndex = remainder.indexOf('/');
                    if(slashIndex != -1) {
                        entries.add(remainder.substring(0, slashIndex + 1));
                    } else {
                        entries.add(remainder);
                    }
                }
            }
        }
        List<String> list = new ArrayList<>(entries);
        Collections.sort(list, (a, b) -> {
            boolean aDir = a.endsWith("/");
            boolean bDir = b.endsWith("/");
            if(aDir && !bDir) return -1;
            if(!aDir && bDir) return 1;
            return a.compareToIgnoreCase(b);
        });
        return list;
    }

    private void init() {
        if(classMap == null)
            classMap = new HashMap<>();
        else
            classMap.clear();
        HashMap<String, ClassDefItem> classMap = ClassListActivity.classMap;
        HashMap<String, ClassDefItem> deleteclassMap = ClassListActivity.deleteclassMap;
        if(dexFile != null && dexFile.ClassDefsSection != null) {
            for(ClassDefItem classItem : dexFile.ClassDefsSection.getItems()) {
                String className = classItem.getClassType().getTypeDescriptor();
                className = className.substring(1, className.length() - 1);
                if(deleteclassMap != null && deleteclassMap.get(className) != null)
                    continue;
                classMap.put(className, classItem);
            }
        }
        pathStack.clear();
        currentPath = "";
        setTitle(title + currentPath);
        classList = listCurrentLevel();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        MenuInflater in = getMenuInflater();
        in.inflate(R.menu.class_list_menu, m);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        int id = mi.getItemId();
        switch(id) {
        case R.id.save_dexfile:
            new Thread(() -> {
                mHandler.sendEmptyMessage(SAVEFILE);
                saveDexFile();
                mHandler.sendEmptyMessage(SAVEDISMISS);
                setResultToZipEditor();
            }).start();
            break;
        case R.id.search_string:
            searchString();
            break;
        case R.id.search_method:
            searchMethod();
            break;
        case R.id.search_field:
            searchField();
            break;
        case R.id.merger_dexfile:
            selectDexFile();
            break;
        }
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
        menu.add(Menu.NONE, R.string.rename_class, Menu.NONE,
                 R.string.rename_class);
        menu.add(Menu.NONE, R.string.remove_class, Menu.NONE,
                 R.string.remove_class);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
        case ActResConstant.class_list_item:
            switch(resultCode) {
            case ActResConstant.add_entry:
                if(mergeDexFile(data.getStringExtra(PerezReverseKillerMain.ENTRYPATH)))
                    toast(getString(R.string.dex_merged));
                break;
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        ProgressDialog dialog = new ProgressDialog(this);
        switch(id) {
        case SAVEFILE:
            dialog.setMessage(getString(R.string.saving));
            break;
        case SEARCH:
            dialog.setMessage(getString(R.string.searching));
            break;
        }
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        return dialog;
    }

    public static void setCurrnetClass(String className) {
        curClassDef = classMap.get(className);
    }

    private void searchString() {
        LayoutInflater inflate = getLayoutInflater();
        ScrollView scroll = (ScrollView) inflate.inflate(
                                R.layout.alert_dialog_search_string, null);
        final EditText srcName = (EditText) scroll.findViewById(R.id.src_edit);
        srcName.setText(searchString);
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.search_string);
        alert.setView(scroll);
        alert.setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
            searchString = srcName.getText().toString();
            if(searchString.length() == 0) {
                toast(getString(R.string.search_name_empty));
                return;
            }
            new Thread(() -> {
                mHandler.sendEmptyMessage(SEARCH);
                List<String> classList = new ArrayList<String>();
                searchStringInMethods(classList, searchString);
                SearchClassesActivity.initClassList(classList);
                mHandler.sendEmptyMessage(SEARCHDISMISS);
                sendIntentToSearchActivity();
            }).start();
        });
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

    private void searchField() {
        LayoutInflater inflate = getLayoutInflater();
        ScrollView scroll = (ScrollView) inflate.inflate(
                                R.layout.alert_dialog_search_field, null);
        final EditText fieldClass = (EditText) scroll
                                    .findViewById(R.id.class_edit);
        final CheckBox ignoreNameAndDescriptor = (CheckBox) scroll
                .findViewById(R.id.ignore_name_descriptor);
        final EditText fieldName = (EditText) scroll
                                   .findViewById(R.id.name_edit);
        final CheckBox ignoreDescriptor = (CheckBox) scroll
                                          .findViewById(R.id.ignore_descriptor);
        final EditText fieldDescriptor = (EditText) scroll
                                         .findViewById(R.id.descriptor_edit);
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.search_field);
        alert.setView(scroll);
        fieldClass.setText(searchFieldClass);
        fieldName.setText(searchFieldName);
        fieldDescriptor.setText(searchFieldDescriptor);
        alert.setPositiveButton(android.R.string.ok,
                (dialog, whichButton) -> new Thread(() -> {
                    mHandler.sendEmptyMessage(SEARCH);
                    searchFieldClass = fieldClass.getText()
                                       .toString();
                    searchFieldName = fieldName.getText()
                                      .toString();
                    searchFieldDescriptor = fieldDescriptor
                                            .getText().toString();
                    List<String> classList = new ArrayList<>();
                    searchFieldInMethods(classList,
                                         searchFieldClass, searchFieldName,
                                         searchFieldDescriptor,
                                         ignoreNameAndDescriptor.isChecked(),
                                         ignoreDescriptor.isChecked());
                    SearchClassesActivity.initClassList(classList);
                    mHandler.sendEmptyMessage(SEARCHDISMISS);
                    sendIntentToSearchActivity();
                }).start());
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

    private void searchMethod() {
        LayoutInflater inflate = getLayoutInflater();
        ScrollView scroll = (ScrollView) inflate.inflate(
                                R.layout.alert_dialog_search_method, null);
        final EditText methodClass = (EditText) scroll
                                     .findViewById(R.id.class_edit);
        final CheckBox ignoreNameAndDescriptor = (CheckBox) scroll
                .findViewById(R.id.ignore_name_descriptor);
        final EditText methodName = (EditText) scroll
                                    .findViewById(R.id.name_edit);
        final CheckBox ignoreDescriptor = (CheckBox) scroll
                                          .findViewById(R.id.ignore_descriptor);
        final EditText methodDescriptor = (EditText) scroll
                                          .findViewById(R.id.descriptor_edit);
        methodClass.setText(searchMethodClass);
        methodName.setText(searchMethodName);
        methodDescriptor.setText(searchMethodDescriptor);
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.search_method);
        alert.setView(scroll);
        alert.setPositiveButton(android.R.string.ok,
                (dialog, whichButton) -> {
                    searchMethodClass = methodClass.getText().toString();
                    searchMethodName = methodName.getText().toString();
                    searchMethodDescriptor = methodDescriptor.getText()
                                             .toString();
                    List<String> classList = new ArrayList<String>();
                    searchMethodInMethods(classList, searchMethodClass,
                                          searchMethodName, searchMethodDescriptor,
                                          ignoreNameAndDescriptor.isChecked(),
                                          ignoreDescriptor.isChecked());
                    SearchClassesActivity.initClassList(classList);
                    sendIntentToSearchActivity();
                });
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

    private void sendIntentToSearchActivity() {
        Intent intent = new Intent(ClassListActivity.this,
                                   SearchClassesActivity.class);
        startActivity(intent);
    }

    private void clearAll() {
        if(classMap != null)
            classMap.clear();
        classMap = null;
        deleteclassMap = null;
        dexFile = null;
        curClassDef = null;
        curFile = null;
        pathStack.clear();
        currentPath = "";
        isChanged = false;
        System.gc();
    }

    private void saveDexFile() {
        DexFile outDexFile = new DexFile();
        HashMap<String, ClassDefItem> classMap = ClassListActivity.classMap;
        HashMap<String, ClassDefItem> deleteclassMap = ClassListActivity.deleteclassMap;
        for(Map.Entry<String, ClassDefItem> entry : classMap.entrySet()) {
            if(deleteclassMap != null
                    && deleteclassMap.get(entry.getKey()) != null)
                continue;
            ClassDefItem classDef = entry.getValue();
            classDef.internClassDefItem(outDexFile);
        }
        outDexFile.setSortAllItems(true);
        outDexFile.place();
        byte[] buf = new byte[outDexFile.getFileSize()];
        ByteArrayAnnotatedOutput out = new ByteArrayAnnotatedOutput(buf);
        outDexFile.writeTo(out);
        DexFile.calcSignature(buf);
        DexFile.calcChecksum(buf);

        if(dexFilePath != null) {
            try (FileOutputStream fos = new FileOutputStream(dexFilePath)) {
                fos.write(buf);
                fos.flush();
            } catch(IOException e) {
                RealFuncUtil.showDlgMsg(this, getString(R.string.code_error), RealFuncUtil.getFullException(e), null);
            }
        }

        outDexFile = null;
        isChanged = false;
    }

    private boolean mergeDexFile(String name) {
        try {
            DexFile tmp = new DexFile(name);
            DexFile dexFile = ClassListActivity.dexFile;
            IndexedSection<ClassDefItem> classes = tmp.ClassDefsSection;
            List<ClassDefItem> classDefList = classes.getItems();
            for(ClassDefItem classDef : classDefList) {
                String className = classDef.getClassType().getTypeDescriptor();
                className = className.substring(1, className.length() - 1);
                if(deleteclassMap != null)
                    deleteclassMap.put(className, null);
                classDef.internClassDefItem(dexFile);
            }
            mod = INIT;
            mAdapter.notifyDataSetInvalidated();
            isChanged = true;
        } catch(Exception e) {
            RealFuncUtil.showDlgMsg(this, getString(R.string.open_dex_error), RealFuncUtil.getFullException(e), null);
            return false;
        }
        System.gc();
        return true;
    }

    private void openStringPool() {
        Intent intent = new Intent(this, TextEditor.class);
        intent.putExtra(TextEditor.PLUGIN, "StringIdsEditor");
        startActivity(intent);
    }

    private void replaceClassType(String src, String dst) {
        for(TypeIdItem type : dexFile.TypeIdsSection.getItems()) {
            String s = type.getTypeDescriptor();

            int pos = 1;
            for(int i = 0; i < s.length(); i++) {
                if(s.charAt(i) != '[')
                    break;
                pos++;
            }
            int i = s.indexOf(src);
            if(i != -1 && i == pos) {
                s = s.replace(src, dst);
                type.setTypeDescriptor(s);
            }
        }
    }

    private void renameType(final String className) {
        final EditText newName = new EditText(this);
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        final boolean isDirectory = className.endsWith("/");
        if(isDirectory)
            newName.setText(className.substring(0, className.length() - 1));
        else
            newName.setText(className);
        alert.setTitle(R.string.rename);
        alert.setView(newName);
        alert.setPositiveButton(android.R.string.ok,
                (dialog, whichButton) -> {
                    String name = newName.getText().toString();
                    if(name.length() == 0 || name.indexOf("/") != -1) {
                        toast(getString(R.string.name_empty));
                        return;
                    } else {
                        for(String s : classList) {
                            if(s.equals(name) || s.equals(name + "/")) {
                                toast(String.format(
                                          getString(R.string.class_exists),
                                          name));
                                return;
                            }
                        }
                    }
                    name += isDirectory ? "/" : "";
                    String cur = currentPath;
                    replaceClassType(cur + className, cur + name);
                    isChanged = true;
                    mod = INIT;
                    mAdapter.notifyDataSetInvalidated();
                });
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

    private void selectDexFile() {
        Intent intent = new Intent(this, PerezReverseKillerMain.class);
        intent.putExtra(PerezReverseKillerMain.SELECTEDMOD, true);
        startActivityForResult(intent, ActResConstant.class_list_item);
    }

    private static void searchStringInMethods(List<String> list, String src) {
        HashMap<String, ClassDefItem> classMap = ClassListActivity.classMap;
        HashMap<String, ClassDefItem> deleteclassMap = ClassListActivity.deleteclassMap;
        for(Map.Entry<String, ClassDefItem> entry : classMap.entrySet()) {
            if(deleteclassMap != null
                    && deleteclassMap.get(entry.getKey()) != null)
                continue;
            ClassDefItem classItem = entry.getValue();
            boolean isSearch = false;
            ClassDataItem classData = classItem.getClassData();
            if(classData != null) {

                ClassDataItem.EncodedMethod[] methods = classData
                                                        .getDirectMethods();
                for(ClassDataItem.EncodedMethod method : methods) {
                    if(DalvikParser.searchStringInMethod(method, src)) {
                        String name = classItem.getClassType()
                                      .getTypeDescriptor();
                        list.add(name.substring(1, name.length() - 1));
                        isSearch = true;
                        break;
                    }
                }
                if(isSearch)
                    continue;

                methods = classData.getVirtualMethods();
                for(ClassDataItem.EncodedMethod method : methods) {
                    if(DalvikParser.searchStringInMethod(method, src)) {
                        String name = classItem.getClassType()
                                      .getTypeDescriptor();
                        list.add(name.substring(1, name.length() - 1));
                        break;
                    }
                }
            }
        }
    }

    private static void searchFieldInMethods(List<String> list,
            String classType, String name, String descriptor,
            boolean ignoreNameAndDescriptor, boolean ignoreDescriptor) {
        HashMap<String, ClassDefItem> classMap = ClassListActivity.classMap;
        HashMap<String, ClassDefItem> deleteclassMap = ClassListActivity.deleteclassMap;
        for(Map.Entry<String, ClassDefItem> entry : classMap.entrySet()) {
            if(deleteclassMap != null
                    && deleteclassMap.get(entry.getKey()) != null)
                continue;
            ClassDefItem classItem = entry.getValue();
            boolean isSearch = false;
            ClassDataItem classData = classItem.getClassData();
            if(classData != null) {

                ClassDataItem.EncodedMethod[] methods = classData
                                                        .getDirectMethods();
                for(ClassDataItem.EncodedMethod method : methods) {
                    if(DalvikParser.searchFieldInMethod(method, classType, name,
                                                  descriptor, ignoreNameAndDescriptor,
                                                  ignoreDescriptor)) {
                        String string = classItem.getClassType()
                                        .getTypeDescriptor();
                        list.add(string.substring(1, string.length() - 1));
                        isSearch = true;
                        break;
                    }
                }
                if(isSearch)
                    continue;

                methods = classData.getVirtualMethods();
                for(ClassDataItem.EncodedMethod method : methods) {
                    if(DalvikParser.searchFieldInMethod(method, classType, name,
                                                  descriptor, ignoreNameAndDescriptor,
                                                  ignoreDescriptor)) {
                        String string = classItem.getClassType()
                                        .getTypeDescriptor();
                        list.add(string.substring(1, string.length() - 1));
                        break;
                    }
                }
            }
        }
    }

    private static void searchMethodInMethods(List<String> list,
            String classType, String name, String descriptor,
            boolean ignoreNameAndDescriptor, boolean ignoreDescriptor) {
        HashMap<String, ClassDefItem> classMap = ClassListActivity.classMap;
        HashMap<String, ClassDefItem> delClassMap = ClassListActivity.deleteclassMap;
        for(Map.Entry<String, ClassDefItem> entry : classMap.entrySet()) {
            if(delClassMap != null
                    && delClassMap.get(entry.getKey()) != null)
                continue;
            ClassDefItem classItem = entry.getValue();
            boolean isSearch = false;
            ClassDataItem classData = classItem.getClassData();
            if(classData != null) {

                ClassDataItem.EncodedMethod[] methods = classData
                                                        .getDirectMethods();
                for(ClassDataItem.EncodedMethod method : methods) {
                    if(DalvikParser.searchMethodInMethod(method, classType, name,
                                                   descriptor, ignoreNameAndDescriptor,
                                                   ignoreDescriptor)) {
                        String string = classItem.getClassType()
                                        .getTypeDescriptor();
                        list.add(string.substring(1, string.length() - 1));
                        isSearch = true;
                        break;
                    }
                }
                if(isSearch)
                    continue;

                methods = classData.getVirtualMethods();
                for(ClassDataItem.EncodedMethod method : methods) {
                    if(DalvikParser.searchMethodInMethod(method, classType, name,
                                                   descriptor, ignoreNameAndDescriptor,
                                                   ignoreDescriptor)) {
                        String string = classItem.getClassType()
                                        .getTypeDescriptor();
                        list.add(string.substring(1, string.length() - 1));
                        break;
                    }
                }
            }
        }
    }

    private void showDialogIfChanged() {
        if(isChanged) {
            RealFuncUtil.showDlgMsg(this, getString(R.string.prompt),
                                   getString(R.string.is_save),
                    (dailog, which) -> {
                        if(which == AlertDialog.BUTTON_POSITIVE) {
                            new Thread(() -> {
                                mHandler.sendEmptyMessage(SAVEFILE);
                                saveDexFile();
                                mHandler.sendEmptyMessage(SAVEDISMISS);
                                setResultToZipEditor();
                            }).start();
                        } else if(which == AlertDialog.BUTTON_NEGATIVE)
                            finish();
                    });
        } else
            finish();
    }

    private void setResultToZipEditor() {
        Intent intent = getIntent();
        setResult(ActResConstant.text_editor, intent);
        finish();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch(ClassCastException e) {
            Log.e(e.toString(), "Bad menuInfo");
            return false;
        }
        switch(item.getItemId()) {
        case R.string.rename_class: {
            String className = classList.get(info.position);
            renameType(className);
        }
        break;
        case R.string.remove_class:
            final String name = classList.get(info.position);
            RealFuncUtil.showDlgMsg(this, getString(R.string.is_remove), name,
                    (dialog, which) -> {
                        if(which == AlertDialog.BUTTON_POSITIVE) {
                            if(isDirectory(name))
                                removeClassesDir(name);
                            else
                                removeClasses(name);
                        }
                    });
            break;
        }
        return true;
    }

    private void removeClassesDir(String name) {
        if(deleteclassMap == null)
            deleteclassMap = new HashMap<>();
        HashMap<String, ClassDefItem> deleteclassMap = ClassListActivity.deleteclassMap;
        String cur = currentPath + name;
        for(String key : classMap.keySet()) {
            if(key.indexOf(cur) == 0)
                deleteclassMap.put(key, classMap.get(key));
        }
        isChanged = true;
        mod = INIT;
        mAdapter.notifyDataSetInvalidated();
    }

    private void removeClasses(String name) {
        if(deleteclassMap == null)
            deleteclassMap = new HashMap<>();
        String cur = currentPath + name;
        deleteclassMap.put(cur, classMap.get(cur));
        isChanged = true;
        mod = INIT;
        mAdapter.notifyDataSetInvalidated();
    }

    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clearAll();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            if(!currentPath.isEmpty()) {
                mod = BACK;
                mAdapter.notifyDataSetInvalidated();
                return true;
            } else {
                showDialogIfChanged();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private class ClassListAdapter extends BaseAdapter {

        protected final Context mContext;
        protected final LayoutInflater mInflater;
        LinearLayout container;

        public ClassListAdapter(Context context) {
            mContext = context;
            mInflater = (LayoutInflater) mContext
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public int getCount() {
            return classList.size();
        }

        public Object getItem(int position) {
            return classList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            String file = classList.get(position);
            if(convertView == null) {
                container = (LinearLayout) mInflater.inflate(
                                R.layout.class_list_item, null);
            } else
                container = (LinearLayout) convertView;
            ImageView icon = (ImageView) container
                             .findViewById(R.id.list_item_icon);
            if(isDirectory(file))
                icon.setImageResource(R.drawable.folder);
            else
                icon.setImageResource(R.drawable.clazz);
            TextView text = (TextView) container
                            .findViewById(R.id.list_item_title);
            text.setText(file);
            return container;
        }
    }
}