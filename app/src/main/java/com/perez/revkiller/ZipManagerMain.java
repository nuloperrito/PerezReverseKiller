package com.perez.revkiller;

import android.app.Dialog;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView;
import android.util.Log;
import android.database.DataSetObserver;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Enumeration;
import java.util.zip.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

import org.jb.dexlib.*;

import com.android.apksigner.ApkSignerTool;
import com.perez.media.AudioPlayerActivity;
import com.perez.arsceditor.ArscActivity;
import com.perez.elfeditor.ElfActivity;
import com.perez.media.HugeImageViewerActivity;
import com.perez.res.*;
import com.perez.util.*;
import com.perez.revkiller.adapter.FileListAdapter;
import com.perez.media.VideoPlayerActivity;

public class ZipManagerMain extends AppCompatActivity {

    public HashMap<String, byte[]> zipEntries;
    public Tree<HashMap<String, byte[]>> tree;
    private ZipFile zipFile;
    private String file;
    private String title = "";
    private boolean isSigned = false;
    private boolean isChanged = false;
    private FileListAdapter mAdapter;
    public List<String> fileList;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case WRITEZIP:
                ZipManagerMain.this.showDialog(R.string.write_zip);
                break;
            case SIGNED:
                ZipManagerMain.this.showDialog(R.string.signed_zip);
                break;
            case LOADING:
                ZipManagerMain.this.showDialog(R.string.load_data);
                break;
            case REMOVE:
                ZipManagerMain.this.showDialog(R.string.zip_remove_progress);
                break;
            case EXTRACT:
                ZipManagerMain.this.showDialog(R.string.extract);
                break;
            case REPLACE:
                ZipManagerMain.this.showDialog(R.string.replacing);
                break;
            case R.string.write_zip:
            case R.string.signed_zip:
            case R.string.load_data:
            case R.string.zip_remove_progress:
            case R.string.extract:
            case R.string.replacing:
                ZipManagerMain.this.dismissDialog(msg.what);
                break;
            case ERROR:
                RealFuncUtil.showDlgMsg(ZipManagerMain.this, "", msg.obj.toString(), null);
                break;
            case TOAST:
                toast(msg.obj.toString());
                break;
            case UPDATE:
                mod = OTHER;
                mAdapter.notifyDataSetInvalidated();
                break;
            }
        }
    };

    private int mod;
    private static final int UNUSE = -1;
    private static final int WRITEZIP = 0;
    private static final int SIGNED = 1;
    private static final int ERROR = 3;
    private static final int LOADING = 4;
    private static final int REMOVE = 5;

    private static final int OPENDIR = 6;
    private static final int BACK = 7;
    private static final int OTHER = 8;
    private static final int UPDATE = 9;

    private static final int EXTRACT = 10;
    private static final int TOAST = 11;
    private static final int REPLACE = 12;

    public ListView lv;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.listact);
        lv = findViewById(R.id.file_list_view);
        String zipPath = getIntent().getStringExtra("FILEPATH");
        if(zipPath == null || !new File(zipPath).exists()) {
            finish();
            return;
        }
        title = zipPath.substring(zipPath.lastIndexOf("/") + 1) + "/";
        if(zipPath.endsWith(".apk")) isSigned = true;
        unzipInMemory(zipPath);
        tree = new Tree(zipEntries.keySet(), zipEntries);
        setTitle(title + tree.getCurPath());
        fileList = tree.list();
        mAdapter = new FileListAdapter(this, false);
        mAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onInvalidated() {
                switch(mod) {
                case OPENDIR:
                    tree.push(file);
                    fileList = tree.list();
                    break;
                case BACK:
                    tree.pop();
                    fileList = tree.list();
                    break;
                case OTHER:
                    fileList = tree.list();
                    break;
                }
                setTitle(title + tree.getCurPath());
            }
        });
        lv.setAdapter(mAdapter);
        registerForContextMenu(lv);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            file = (String) parent.getItemAtPosition(position);
            if(tree.isDirectory(file)) {
                mod = OPENDIR;
                mAdapter.notifyDataSetInvalidated();
                return;
            }
            mod = UNUSE;
            Intent intent;
            switch (RealFuncUtil.getFileExtension(file)) {
                case "arsc":
                    new Thread(() -> {
                        mHandler.sendEmptyMessage(LOADING);
                        textEditArsc(file);
                        mHandler.sendEmptyMessage(R.string.load_data);
                    }).start();
                    break;
                case "xml":
                    new Thread(() -> {
                        mHandler.sendEmptyMessage(LOADING);
                        textEditAxml(file);
                        mHandler.sendEmptyMessage(R.string.load_data);
                    }).start();
                    break;
                case "dex":
                    new Thread(() -> {
                        mHandler.sendEmptyMessage(LOADING);
                        openDexFile(file);
                        mHandler.sendEmptyMessage(R.string.load_data);
                    }).start();
                    break;
                // TODO: FIX EXTRACTION BELOW
                case "so":
                    PELF(new File(file));
                    break;
                case "mp4":
                case "3gp":
                case "mkv":
                    intent = new Intent(ZipManagerMain.this, VideoPlayerActivity.class);
                    intent.setData(Uri.parse(file));
                    startActivity(intent);
                    break;
                case "mp3":
                case "aac":
                case "ogg":
                case "wma":
                case "wav":
                case "amr":
                case "flac":
                case "m4a":
                    intent = new Intent(ZipManagerMain.this, AudioPlayerActivity.class);
                    intent.setData(Uri.parse(file));
                    startActivity(intent);
                    break;
                case "jpg":
                case "png":
                case "bmp":
                case "gif":
                case "webp":
                    intent = new Intent(ZipManagerMain.this, HugeImageViewerActivity.class);
                    intent.setData(Uri.parse(file));
                    startActivity(intent);
                    break;
                default:
                    break;
            }
        });
    }

    public void PELF(File name) {
        if(Features.isValidElf(name.toString())) {
            Intent i = new Intent(this, ElfActivity.class);
            i.putExtra("FILE_NAME", name.toString());
            startActivity(i);
            this.mAdapter.notifyDataSetInvalidated();
        } else Toast.makeText(ZipManagerMain.this, getString(R.string.invalid_elf), Toast.LENGTH_LONG).show();
    }

    private void resultToFileBrowser() {
        Intent intent = new Intent();
        setResult(ActResConstant.zip_list_item, intent);
        finish();
    }

    private void openDexFile(String file) {
        try {
            byte[] data = readEntry(file);
            ClassListActivity.dexFile = new DexFile(data);
            Intent intent = new Intent(this, ClassListActivity.class);
            startActivityForResult(intent, ActResConstant.zip_list_item);
        } catch(Exception e) {
            Message msg = new Message();
            msg.what = ERROR;
            msg.obj = e.getMessage();
            mHandler.sendMessage(msg);
        }
    }

    private boolean replaceAxml(String name, String src, String dst) {
        boolean isReplace = false;
        try {
            ArrayList<String> data = new ArrayList<>();
            AXmlDecoder axml = AXmlDecoder.read(new ByteArrayInputStream(readEntryAbsName(name)));
            axml.mTableStrings.getStrings(data);
            for(int i = 0, len = data.size(); i < len; i++) {
                String s = data.get(i);
                if(s.contains(src)) {
                    isReplace = true;
                    data.set(i, s.replace(src, dst));
                }
            }
            if(isReplace) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                axml.write(data, out);
                zipEntries.put(name, out.toByteArray());
                isChanged = true;
            }
        } catch(Exception e) {
        }
        System.gc();
        return isReplace;
    }

    private int replaceAllAxml(String src, String dst) {
        int count = 0;
        for(String name : zipEntries.keySet()) {
            if(name.toLowerCase().endsWith(".xml")) {
                if(replaceAxml(name, src, dst))
                    count++;
            }
        }
        return count;
    }

    private void replace() {
        LayoutInflater inflate = getLayoutInflater();
        LinearLayout line = (LinearLayout) inflate.inflate(R.layout.alert_dialog_replace_axml, null);
        final EditText srcName = line.findViewById(R.id.src_edit);
        final EditText dstName = line.findViewById(R.id.dst_edit);
        srcName.setText("");
        dstName.setText("");
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.replace_axml);
        alert.setView(line);
        alert.setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
            final String src = srcName.getText().toString();
            final String dst = dstName.getText().toString();
            if(src.isEmpty()) {
                toast(getString(R.string.search_name_empty));
                return;
            }
            new Thread(() -> {
                mHandler.sendEmptyMessage(REPLACE);
                int count = replaceAllAxml(src, dst);
                if(count > 0) {
                    Message msg = new Message();
                    msg.what = TOAST;
                    msg.obj = getString(R.string.replace_count) + count;
                    mHandler.sendMessage(msg);
                }
                mHandler.sendEmptyMessage(R.string.replacing);
            }).start();
        });
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }
    private void unzipInMemory(String name) {
        if(zipEntries != null)
            return;
        zipEntries = new HashMap<>();
        try {
            zipFile = new ZipFile(name);
            readZip(zipFile, zipEntries);
        } catch(IOException e) {
            zipEntries.put(e.getMessage(), null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        MenuInflater in = getMenuInflater();
        in.inflate(R.menu.zip_editor_menu, m);
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clearAll();
    }

    public void clearAll() {
        zipEntries = null;
        zipFile = null;
        file = null;
        System.gc();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        int id = mi.getItemId();
        switch(id) {
        case R.id.add_entry:
            selectFile();
            break;
        case R.id.save_file:
            saveFile();
            break;
        case R.id.replace_axml:
            replace();
            break;
        }
        return true;
    }

    private void showDialog() {
        RealFuncUtil.showDlgMsg(this, getString(R.string.prompt), getString(R.string.is_save),
                (dailog, which) -> {
                    if(which == AlertDialog.BUTTON_POSITIVE)
                        saveFile();
                    else if(which == AlertDialog.BUTTON_NEGATIVE)
                        finish();
                });
    }

    private void saveFile() {
        new Thread(() -> {
            String out = zipFile.getName();
            int i = out.lastIndexOf(".");
            if(i != -1)
                out = out.substring(0, i) + (isSigned ? ".signed" : ".new") + out.substring(i);
            try {
                if(isSigned) {
                    mHandler.sendEmptyMessage(WRITEZIP);
                    File temp = File.createTempFile("mao", ".tmp", getCacheDir());
                    temp.deleteOnExit();
                    recompress(zipFile, zipEntries, temp);
                    ApkSignerTool.sign(ZipManagerMain.this, temp, out);
                    temp.delete();
                } else {
                    mHandler.sendEmptyMessage(WRITEZIP);
                    File file = new File(out);
                    recompress(zipFile, zipEntries, file);
                }
            } catch(Exception e) {
                Message msg = new Message();
                msg.what = ERROR;
                msg.obj = e.getMessage();
                mHandler.sendMessage(msg);
                e.printStackTrace();
                mHandler.sendEmptyMessage(R.string.write_zip);
                return;
            }
            mHandler.sendEmptyMessage(R.string.write_zip);
            resultToFileBrowser();
        }).start();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        menu.add(Menu.NONE, R.string.zip_editor_remove, Menu.NONE, R.string.zip_editor_remove);
        menu.add(Menu.NONE, R.string.extract, Menu.NONE, R.string.extract);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        ProgressDialog dialog = new ProgressDialog(this);
        switch(id) {
        case R.string.write_zip:
            dialog.setMessage(getString(R.string.write_zip));
            break;
        case R.string.load_data:
            dialog.setMessage(getString(R.string.load_data));
            break;
        case R.string.signed_zip:
            dialog.setMessage(getString(R.string.signed_zip));
            break;
        case R.string.zip_remove_progress:
            dialog.setMessage(getString(R.string.zip_remove_progress));
            break;
        case R.string.extract:
            dialog.setMessage(getString(R.string.extracting));
            break;
        case R.string.replacing:
            dialog.setMessage(getString(R.string.replacing));
            break;
        }
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        return dialog;
    }

    @Override
    public void onConfigurationChanged(Configuration conf) {
        super.onConfigurationChanged(conf);
    }

    private void selectFile() {
        Intent intent = new Intent(this, ZipManagerMain.class);
        intent.putExtra(PerezReverseKillerMain.SELECTEDMOD, true);
        startActivityForResult(intent, ActResConstant.zip_list_item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case ActResConstant.zip_list_item:
                switch(resultCode) {
                case ActResConstant.add_entry:
                    final String name = data.getStringExtra(PerezReverseKillerMain.ENTRYPATH);
                    new Thread(() -> {
                        mHandler.sendEmptyMessage(LOADING);
                        File file = new File(name);
                        byte[] b = null;
                        try {
                            b = FileUtil.readFile(file);
                        } catch(IOException io) {
                        }
                        zipEntries.put(tree.getCurPath() + file.getName(), b);
                        isChanged = true;
                        tree.addNode(file.getName());
                        Message msg = new Message();
                        msg.what = TOAST;
                        msg.obj = getString(R.string.file_added);
                        mHandler.sendMessage(msg);
                        // dismissDialog
                        mHandler.sendEmptyMessage(R.string.load_data);
                        mHandler.sendEmptyMessage(UPDATE);
                    }).start();
                    break;
                case ActResConstant.text_editor:
                    zipEntries.put(getCurFile(), TextEditor.data);
                    isChanged = true;
                    mAdapter.notifyDataSetInvalidated();
                    TextEditor.data = null;
                    toast(getString(R.string.saved));
                    System.gc();
                    break;
                }
            break;
        }
    }

    public String getCurFile() {
        return tree.getCurPath() + file;
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
        final String name = (String) mAdapter.getItem(info.position);
        int id = item.getItemId();
        switch(id) {
        case R.string.zip_editor_remove:
            RealFuncUtil.showDlgMsg(this, getString(R.string.is_remove), name, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if(which == AlertDialog.BUTTON_POSITIVE) {
                        new Thread(() -> {
                            mHandler.sendEmptyMessage(REMOVE);
                            if(tree.isDirectory(name))
                                removeDirectory(name);
                            else
                                removeFile(name);
                            mHandler.sendEmptyMessage(R.string.zip_remove_progress);
                            tree = new Tree(zipEntries.keySet(), zipEntries);
                            mHandler.sendEmptyMessage(UPDATE);
                        }).start();
                    }
                }
            });
            break;
        case R.string.extract:
            new Thread(() -> {
                mHandler.sendEmptyMessage(EXTRACT);
                Message msg = new Message();
                try {
                    extractItem(name);
                    msg.what = TOAST;
                    msg.obj = getString(R.string.extracted);
                } catch(Exception e) {
                    msg.what = ERROR;
                    msg.obj = e.getMessage();
                    mHandler.sendMessage(msg);
                    mHandler.sendEmptyMessage(R.string.extract);
                } finally {
                    mHandler.sendMessage(msg);
                    mHandler.sendEmptyMessage(R.string.extract);
                }
            }).start();
            break;
        }
        return true;
    }

    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            if(!getTitle().equals(title)) {
                mod = BACK;
                mAdapter.notifyDataSetInvalidated();
                return true;
            } else {
                if(isChanged)
                    showDialog();
                else
                    finish();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void removeFile(String name) {
        zipEntries.remove(tree.getCurPath() + name);
    }

    private void removeDirectory(String name) {
        Map<String, byte[]> zipEnties = zipEntries;
        String curr = tree.getCurPath();
        Set<String> keySet = zipEnties.keySet();
        String[] keys = new String[keySet.size()];
        keySet.toArray(keys);
        for(String key : keys) {
            if(key.startsWith(curr + name))
                zipEnties.remove(key);
        }
    }

    private void textEditArsc(String file) {
        try {
            Intent it = new Intent(ZipManagerMain.this, ArscActivity.class);
            Log.v("ZipManager", "File path in ZIP is:" + file);
            startActivityForResult(it, ActResConstant.list_item_details);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void textEditAxml(String file) {
        byte[] data = readEntry(file);
        TextEditor.data = data;
        Intent intent = new Intent(this, TextEditor.class);
        intent.putExtra(TextEditor.PLUGIN, "AXmlEditor");
        startActivityForResult(intent, ActResConstant.zip_list_item);
    }

    private byte[] readEntry(String name) {
        byte[] buf = zipEntries.get(tree.getCurPath() + name);
        if(buf == null)
            return readEntryForZip(tree.getCurPath() + name);
        return buf;
    }

    private void extractItem(String name) throws Exception {
        String str = zipFile.getName();
        int s = str.lastIndexOf('/');
        int e = str.lastIndexOf('.');
        if(s < e)
            str = str.substring(s, e);
        File outPath = new File(new File(zipFile.getName()).getParent() + str);
        String curr = tree.getCurPath();
        curr = tree.isDirectory(name) ? curr + name + "/" : curr + name;
        for(String key : zipEntries.keySet()) {
            if(key.startsWith(curr)) {
                byte[] buf = zipEntries.get(key);
                if(buf != null)
                    ZipExtract.extractEntryForByteArray(buf, key, outPath);
                else {
                    ZipEntry entry = zipFile.getEntry(key);
                    ZipExtract.extractEntry(zipFile, entry, outPath);
                }
            }
        }
    }

    private byte[] readEntryAbsName(String name) {
        byte[] buf = zipEntries.get(name);
        if(buf == null)
            return readEntryForZip(name);
        return buf;
    }

    public ZipEntry getEntry(String name) {
        byte[] buf = zipEntries.get(tree.getCurPath() + name);
        if(buf == null) {
            if(zipFile != null)
                return zipFile.getEntry(tree.getCurPath() + name);
            ZipEntry zipEntry = new ZipEntry(tree.getCurPath() + name);
            zipEntry.setTime(0);
            zipEntry.setSize(0);
            return zipEntry;
        }
        ZipEntry zipEntry = new ZipEntry(tree.getCurPath() + name);
        zipEntry.setTime(System.currentTimeMillis());
        zipEntry.setSize(buf.length);
        return zipEntry;
    }

    private byte[] readEntryForZip(String name) {
        ZipEntry zipEntry = zipFile.getEntry(name);
        if(zipEntry != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(8 * 1024);
            byte[] buf = new byte[4 * 1024];
            try {
                InputStream in = zipFile.getInputStream(zipEntry);
                int count;
                while((count = in.read(buf, 0, buf.length)) != -1)
                    baos.write(buf, 0, count);
                in.close();
                baos.close();
            } catch (IOException io) {
            }
            return baos.toByteArray();
        }
        return null;
    }

    private static void readZip(ZipFile zip, Map<String, byte[]> map) throws IOException {
        Enumeration<? extends ZipEntry> enums = zip.entries();
        while(enums.hasMoreElements()) {
            ZipEntry entry = enums.nextElement();
            if(!entry.isDirectory())
                map.put(entry.getName(), null);
        }
    }

    public static void recompress(ZipFile zipFile, Map<String, byte[]> map, File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        ZipOutputStream zos = new ZipOutputStream(out);
        byte[] buf = new byte[20 * 1024];
        System.out.println(map.keySet().size());
        for(String key : map.keySet()) {
            byte[] data = map.get(key);
            System.out.println(key);
            if(data != null) {
                ZipEntry zipEntry = new ZipEntry(key);
                zipEntry.setSize(data.length);
                zipEntry.setTime(System.currentTimeMillis());
                zos.putNextEntry(zipEntry);
                zos.write(data);
            } else {
                ZipEntry zipEntry = zipFile.getEntry(key);
                if(zipEntry != null) {
                    InputStream in = zipFile.getInputStream(zipEntry);
                    zos.putNextEntry(zipEntry);
                    int count;
                    while((count = in.read(buf, 0, buf.length)) != -1)
                        zos.write(buf, 0, count);
                }
            }
            zos.flush();
        }
        zos.close();
    }
}
