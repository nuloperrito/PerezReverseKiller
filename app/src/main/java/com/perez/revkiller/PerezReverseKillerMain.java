package com.perez.revkiller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.provider.Settings;
import android.system.ErrnoException;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.apksigner.ApkSignerTool;
import net.lightbody.bmp.util.J2DMain;
import com.perez.arsceditor.ArscActivity;
import com.perez.elfeditor.ElfActivity;
import com.perez.media.HugeImageViewerActivity;
import com.perez.javah.TargetLanguage;
import com.perez.javah.JavahTask;
import com.perez.media.AudioPlayerActivity;
import com.perez.media.VideoPlayerActivity;
import com.perez.netdiag.Activity.NDGAct;
import com.perez.palette.SketchActivity;
import com.perez.qrcode.QRCodeCamActivity;
import com.perez.exifremover.Interfaz;
import com.perez.revkiller.adapter.FileListAdapter;
import com.perez.util.FileUtil;
import com.perez.util.RankPrefUtil;
import com.perez.util.RealFuncUtil;
import com.perez.util.TelephonyCallbackApi31;
import com.perez.util.VineflowerJarDecompiler;
import com.perez.util.ZipExtract;
import com.perez.xml2axml.func.FuncMain;

import net.lightbody.bmp.util.BakSmaliFunc;
import org.jb.dexlib.DexFile;
import org.jf.smali.Main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;
import java.util.Vector;
import java.util.zip.ZipFile;

public class PerezReverseKillerMain extends AppCompatActivity {
    public final static String ENTRYPATH = "ZipEntry";
    public final static String SELECTEDMOD = "selected_mod";
    public final static String TAG = "PerezReverseKillerMain";

    public static final int SHOWPROGRESS = 1;
    public static final int DISMISSPROGRESS = 2;
    public static final int TOAST = 3;
    public static final int SHOWMESSAGE = 5;

    public static final int RQ_PERMISSION = 0xffee;

    public boolean initialized = false;

    private Stack<Integer> pos = new Stack<>();

    public static List<File> mFileList;
    private FileListAdapter mAdapter;
    private boolean mSelectMod = false;
    private boolean m_isPreparedToBuildSmali = false;
    private File mCurrentDir;
    private File mCurrent;
    private ListView fileList;
    private SwipeRefreshLayout srLayout;

    public int position;

    private static boolean mCut;
    private static File mClipboard;
    private Dialog mPermissionDialog;
    private volatile boolean isCallForwardingEnabled = false;
    private PhoneStateListener mRealtimePhoneStateListener;

    private static class DialogMsg {
        private String title;
        private String msg;
        DialogMsg(String t, String m) {
            this.title = t;
            this.msg = m;
        }
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case SHOWPROGRESS:
                PerezReverseKillerMain.this.showDialog(0);
                break;
            case DISMISSPROGRESS:
                mAdapter.notifyDataSetInvalidated();
                PerezReverseKillerMain.this.dismissDialog(0);
                break;
            case TOAST:
                showToast(true, msg.obj.toString());
                break;
            case SHOWMESSAGE:
                if(msg.obj instanceof DialogMsg) {
                    DialogMsg dm = (DialogMsg) msg.obj;
                    RealFuncUtil.showDlgMsg(PerezReverseKillerMain.this, dm.title, dm.msg, null);
                }
                break;
            }
        }
    };
    private DataSetObserver dataSetObserver = new DataSetObserver() {
        @Override
        public void onInvalidated() {
        updateAndFilterFileList("");
        }
    };

    private void CreateInit() {
        fileList = findViewById(R.id.file_list_view);
        mSelectMod = getIntent().getBooleanExtra(SELECTEDMOD, false);
        if(mCurrentDir == null) {
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
                mCurrentDir = Environment.getExternalStorageDirectory();
            else
                mCurrentDir = Environment.getRootDirectory();
        }
        mAdapter = new FileListAdapter(getApplication(), true);
        mAdapter.registerDataSetObserver(dataSetObserver);
        registerForContextMenu(fileList);
        updateAndFilterFileList("");
        fileList.setAdapter(mAdapter);
        if(mPermissionDialog == null) {
            mPermissionDialog = new Dialog(this);
            mPermissionDialog.setContentView(R.layout.permissions);
            mPermissionDialog.findViewById(R.id.btnOk).setOnClickListener(v -> setPermissions());
            mPermissionDialog.findViewById(R.id.btnCancel).setOnClickListener(v -> mPermissionDialog.hide());
        }
        fileList.setSelection(position);
        fileList.setOnItemClickListener((parent, view, position, id) -> {
            final File file = (File) parent.getItemAtPosition(position);
            PerezReverseKillerMain.this.position = position;
            mCurrent = file;
            if(file.isDirectory()) {
                if(file.toString().endsWith("_baksmali")) {
                    m_isPreparedToBuildSmali = true;
                    new AlertDialog.Builder(PerezReverseKillerMain.this).setTitle(getString(R.string.tips)).
                            setMessage(getString(R.string.smali_instruction)).setPositiveButton(getString(R.string.build_smali), (arg0, arg1) -> {
                                buildSmali(file);
                                m_isPreparedToBuildSmali = false;
                            }).setNeutralButton(getString(R.string.explore_dir), (arg0, arg1) -> {
                                mCurrentDir = file;
                                pos.push(parent.getFirstVisiblePosition());
                                mAdapter.notifyDataSetInvalidated();
                                m_isPreparedToBuildSmali = false;
                            }).show();
                } else {
                    mCurrentDir = file;
                    pos.push(parent.getFirstVisiblePosition());
                    mAdapter.notifyDataSetInvalidated();
                    return;
                }
            }
            if(mSelectMod) {
                mSelectMod = false;
                resultFileToZipEditor(file);
                return;
            }
            if(RealFuncUtil.isZip(file))
                openZipLike(file);
            else {
                Intent intent;
                switch (RealFuncUtil.getFileExtension(file.getName())) {
                    case "mp4":
                    case "mkv":
                    case "3gp":
                        intent = new Intent(PerezReverseKillerMain.this, VideoPlayerActivity.class);
                        intent.setData(Uri.parse(file.toString()));
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
                        intent = new Intent(PerezReverseKillerMain.this, AudioPlayerActivity.class);
                        intent.setData(Uri.parse(file.toString()));
                        startActivity(intent);
                        break;
                    case "jpg":
                    case "png":
                    case "bmp":
                    case "gif":
                    case "webp":
                        intent = new Intent(PerezReverseKillerMain.this, HugeImageViewerActivity.class);
                        intent.setData(Uri.parse(file.toString()));
                        startActivity(intent);
                        break;
                    case "rar":
                        ExtractRar(file);
                        break;
                    case "arsc":
                        editArsc(file);
                        break;
                    case "xml":
                        procXml(file);
                        break;
                    case "dex":
                        openDexFile(file);
                        break;
                    case "odex":
                        ConOdex(file);
                        break;
                    case "oat":
                        OatToDex(file);
                        break;
                    case "so":
                        PELF(file);
                        break;
                    case "txt":
                    case "log":
                    case "c":
                    case "cpp":
                    case "h":
                    case "hpp":
                    case "java":
                    case "kt":
                    case "py":
                    case "cs":
                    case "smali":
                    case "js":
                    case "json":
                    case "html":
                    case "rs":
                        editText(file);
                        break;
                    default:
                        if(!m_isPreparedToBuildSmali) dialogMenu();
                        break;
                }
            }
        });
        srLayout = findViewById(R.id.swipeRefresh);
        srLayout.setOnRefreshListener(() -> {
            mAdapter.notifyDataSetInvalidated();
            srLayout.setRefreshing(false);
        });
        initRealtimeCallForwardingListener();
        initialized = true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if(mSelectMod)
            return;
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.setHeaderTitle(R.string.options);
        File file;
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            file = (File) fileList.getItemAtPosition(info.position);
            if(!file.isDirectory())
                menu.add(Menu.NONE, R.string.view, Menu.NONE, R.string.view);
        } catch(ClassCastException e) {
            Log.e(TAG, "Bad menuInfo" + e);
            return;
        }
        String ext_name = file.getName().substring(file.getName().lastIndexOf(".") + 1).toLowerCase();
        menu.add(Menu.NONE, R.string.delete, Menu.NONE, R.string.delete);
        menu.add(Menu.NONE, R.string.rename, Menu.NONE, R.string.rename);
        if(RealFuncUtil.isZip(file)) {
            menu.add(Menu.NONE, R.string.signed, Menu.NONE, R.string.signed);
            menu.add(Menu.NONE, R.string.extract_all, Menu.NONE, R.string.extract_all);
            menu.add(Menu.NONE, R.string.zipalign, Menu.NONE, R.string.zipalign);
        }
        menu.add(Menu.NONE, R.string.copy, Menu.NONE, R.string.copy);
        menu.add(Menu.NONE, R.string.cut, Menu.NONE, R.string.cut);
        menu.add(Menu.NONE, R.string.paste, Menu.NONE, R.string.paste);
        menu.add(Menu.NONE, R.string.permission, Menu.NONE, R.string.permission);
        switch (ext_name) {
            case "c":
            case "cpp":
            case "h":
            case "hpp":
            case "java":
            case "cs":
                menu.add(Menu.NONE, R.string.fmt_code, Menu.NONE, R.string.fmt_code);
                break;
            case "jpg":
            case "jpeg":
                menu.add(Menu.NONE, R.string.del_exif, Menu.NONE, R.string.del_exif);
                menu.add(Menu.NONE, R.string.str_jpg2png, Menu.NONE, R.string.str_jpg2png);
                break;
            case "png":
                menu.add(Menu.NONE, R.string.str_png2jpg, Menu.NONE, R.string.str_png2jpg);
                break;
            case "class":
                menu.add(Menu.NONE, R.string.gen_jni, Menu.NONE, R.string.gen_jni);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        try {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            mCurrent = (File) fileList.getItemAtPosition(info.position);
            position = info.position;
            switch(item.getItemId()) {
                case R.string.delete:
                    delete(mCurrent);
                    return true;
                case R.string.view:
                    viewCurrent();
                    return true;
                case R.string.extract_all:
                    extractAll(mCurrent);
                    return true;
                case R.string.zipalign:
                    zipAlign(mCurrent);
                    return true;
                case R.string.signed:
                    digitalSignApk(mCurrent);
                    return true;
                case R.string.rename:
                    Rename(mCurrent);
                    return true;
                case R.string.copy:
                    addCopy(mCurrent);
                    return true;
                case R.string.cut:
                    addCut(mCurrent);
                    return true;
                case R.string.paste:
                    pasteFile();
                    return true;
                case R.string.permission:
                    showPermissions();
                    return true;
                case R.string.fmt_code:
                    new Thread(() -> {
                        if(RealFuncUtil.formatCode(this,mCurrent.getPath())) {
                            showToast(false, getString(R.string.format_code_success));
                            runOnUiThread(() -> mAdapter.notifyDataSetInvalidated());
                        }
                        else showToast(false, getString(R.string.format_code_failed));
                    }).start();
                    return true;
                case R.string.del_exif:
                    new Thread(() -> {
                        try {
                            Interfaz.deleteEXIF(mCurrent.getPath(), mCurrent.getPath());
                            showToast(false, getString(R.string.remove_exif_success));
                            runOnUiThread(() -> mAdapter.notifyDataSetInvalidated());
                        } catch (Exception e) {
                            runOnUiThread(() -> RealFuncUtil.showDlgMsg(PerezReverseKillerMain.this,
                                    getString(R.string.remove_exif_failed), RealFuncUtil.getFullException(e), null));
                        }
                    }).start();
                    return true;
                case R.string.str_jpg2png:
                    FileUtil.convertToPng(mCurrent.getPath(), getFileNameNoEx(mCurrent.getPath()) + ".png");
                    mAdapter.notifyDataSetInvalidated();
                    return true;
                case R.string.str_png2jpg:
                    FileUtil.convertToJpg(mCurrent.getPath(), getFileNameNoEx(mCurrent.getPath()) + ".jpg");
                    mAdapter.notifyDataSetInvalidated();
                    return true;
                case R.string.gen_jni: {
                    showGenJniDialog(mCurrent, mCurrentDir);
                    return true;
                }
            }
        } catch(ClassCastException e) {
            return false;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            if(mFileList != null && !mFileList.isEmpty()) {
                File first = mFileList.get(0);
                if(first.getName().equals("..") && first.getParentFile() != null) {
                    mCurrentDir = first;
                    mAdapter.notifyDataSetInvalidated();
                    if(!pos.empty())
                        fileList.setSelection(pos.pop());
                    return true;
                }
            }
            if(mCurrentDir != null && mCurrentDir.getParentFile() != null) {
                mCurrentDir = mCurrentDir.getParentFile();
                mAdapter.notifyDataSetInvalidated();
                if(!pos.empty())
                    fileList.setSelection(pos.pop());
                return true;
            }
            if(mCurrentDir != null && mCurrentDir.getParent() == null) {
                finish();
                if(!mSelectMod)
                    System.exit(0);
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RQ_PERMISSION && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CreateInit();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(PerezReverseKillerMain.this);
                builder.setTitle(R.string.warning);
                builder.setMessage(R.string.lack_perms);
                builder.setPositiveButton(android.R.string.ok, (dialog, which) -> Process.killProcess(Process.myPid()));
                builder.show();
            }
        }
    }

    public boolean hasPermission(Context ctx, String[] perms) {
        for(String perm : perms) {
            if(ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) return true;
        }
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.listact);
        String[] pmList1 = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE};
        String[] pmList2 = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (hasPermission(this, pmList1) || !Environment.isExternalStorageManager())) {
            ActivityCompat.requestPermissions(PerezReverseKillerMain.this, pmList1, RQ_PERMISSION);
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + this.getPackageName()));
            startActivityForResult(intent, RQ_PERMISSION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && hasPermission(this, pmList2)) {
            ActivityCompat.requestPermissions(PerezReverseKillerMain.this, pmList2, RQ_PERMISSION);
        } else {
            CreateInit();
        }
    }

    public static String addSuffixToExtension(String filePath, String suffix) {
        int lastDotIndex = filePath.lastIndexOf(".");
        if (lastDotIndex != -1) {
            String filenameWithoutExtension = filePath.substring(0, lastDotIndex);
            String extension = filePath.substring(lastDotIndex);
            return filenameWithoutExtension + "_" + suffix + extension;
        }
        return filePath;
    }

    private String getFileNameNoEx(String filename) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex != -1) ? filename.substring(0, dotIndex) : filename;
    }

    private void updateAndFilterFileList(final String query) {
        File[] files = mCurrentDir.listFiles();
        if(files != null) {
            setTitle(mCurrentDir.getPath());
            List<File> work = new Vector<>(files.length);
            for(File file : files) {
                if(query == null || query.isEmpty())
                    work.add(file);
                else if(file.getName().toLowerCase().contains(query.toLowerCase()))
                    work.add(file);
            }
            RankPrefUtil rpf = new RankPrefUtil(this);
            switch(rpf.GetWhich()) {
                case "type":
                    Collections.sort(work, RankPrefUtil.sortByType);
                    break;
                case "date":
                    Collections.sort(work, RankPrefUtil.sortByDate);
                    break;
                case "size":
                    Collections.sort(work, RankPrefUtil.sortBySize);
                    break;
                case "name":
                default:
                    Collections.sort(work, RankPrefUtil.sortByName);
                    break;
            }
            if(rpf.GetReverse()) Collections.reverse(work);
            mFileList = work;
            File parent = mCurrentDir.getParentFile();
            if(parent != null) {
                mFileList.add(0, new File(Objects.requireNonNull(mCurrentDir.getParent())) {
                    @Override
                    public boolean isDirectory() {
                        return true;
                    }
                    @NonNull
                    @Override
                    public String getName() {
                        return "..";
                    }
                });
            }
        }
    }

    private void resultFileToZipEditor(File file) {
        Intent intent = getIntent();
        intent.putExtra(ENTRYPATH, file.getAbsolutePath());
        setResult(ActResConstant.add_entry, intent);
        finish();
    }

    private void procXml(final File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(PerezReverseKillerMain.this);
        builder.setTitle(getString(R.string.tips));
        String message;
        Runnable positiveAction, neutralAction;

        if (FuncMain.isBinAXML(file.toString())) {
            message = getString(R.string.axml_instruction);
            positiveAction = () -> {
                boolean success = false;
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                try {
                    FuncMain.decode(file.toString(), addSuffixToExtension(file.toString(), "_dec"));
                    success = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
                if (success) {
                    showToast(false, getString(R.string.dec_axml_failed));
                } else {
                    showToast(false, getString(R.string.dec_axml_success));
                }
            };
            neutralAction = () -> editAxml(file);
        } else {
            message = getString(R.string.xml_instruction);
            positiveAction = () -> {
                boolean success = false;
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                try {
                    FuncMain.encode(PerezReverseKillerMain.this, file.toString(),
                            addSuffixToExtension(file.toString(), "_comp"));
                    success = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
                if (success) {
                    showToast(false, getString(R.string.comp_xml_failed));
                } else {
                    showToast(false, getString(R.string.comp_xml_success));
                }
            };
            neutralAction = () -> editText(file);
        }

        builder.setMessage(message);
        builder.setPositiveButton(R.string.btn_dec, (dialog, which) -> positiveAction.run());
        builder.setNeutralButton(R.string.btn_edit_axml, (dialog, which) -> neutralAction.run());
        builder.show();
    }

    public void openZipLike(final File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.tips));

        if (file.toString().endsWith(".jar") && RealFuncUtil.isStandardJAR(file.toString())) {
            builder.setMessage(getString(R.string.jar_instruction));
            builder.setPositiveButton(getString(R.string.todex), (dialog, whichButton) -> new Thread(() -> {
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                boolean JAR2DEX_SUC = false;
                try {
                    JAR2DEX_SUC = J2DMain.JarToDex(file.toString(),
                            file.toString().substring(0, file.toString().length() - 4) + "_converted.dex");
                } catch(IOException e) {
                    e.printStackTrace();
                }
                showToast(false, JAR2DEX_SUC ? getString(R.string.jar2dex_fail) : getString(R.string.jar2dex_success));
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
            }).start());
            builder.setNegativeButton(getString(R.string.decompile_jar), (dialog, which) -> new Thread(() -> {
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                try {
                    VineflowerJarDecompiler.decompile(file,
                            new File(file.getParent() + "/" + getFileNameNoEx(file.getName()) + ".zip"));
                    showToast(false, getString(R.string.djar_success));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
            }).start());
            builder.setNeutralButton(getString(R.string.explore_jar), (dialog, whichButton) -> {
                Intent intent = new Intent(PerezReverseKillerMain.this, ZipManagerMain.class);
                intent.putExtra("FILEPATH", file.getAbsolutePath());
                startActivityForResult(intent, ActResConstant.list_item_details);
            });
            builder.show();
        } else if (file.toString().endsWith(".apk")) {
            builder.setMessage(getString(R.string.apk_instruction));
            builder.setPositiveButton(getString(R.string.open_apk), (dialog, whichButton) -> {
                Intent intent = new Intent(PerezReverseKillerMain.this, ZipManagerMain.class);
                intent.putExtra("FILEPATH", file.getAbsolutePath());
                startActivityForResult(intent, ActResConstant.list_item_details);
            });
            builder.setNegativeButton(getString(R.string.decompile_javainapk), (dialog, which) -> {
                        Intent intent = new Intent(PerezReverseKillerMain.this, PackageActivity.class);
                        intent.putExtra("fileName", file.toString());
                        PerezReverseKillerMain.this.startActivity(intent);
                    });
            builder.show();
        } else {
            Intent intent = new Intent(this, ZipManagerMain.class);
            intent.putExtra("FILEPATH", file.getAbsolutePath());
            startActivityForResult(intent, ActResConstant.list_item_details);
        }
    }

    private void editArsc(final File file) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            try {
                Intent it = new Intent(PerezReverseKillerMain.this, ArscActivity.class);
                it.putExtra("FilePath", file.toString());
                startActivityForResult(it, ActResConstant.list_item_details);
            } catch(Exception e) {
                Message msg = new Message();
                msg.what = SHOWMESSAGE;
                msg.obj = new DialogMsg(getString(R.string.open_arsc_failed), RealFuncUtil.getFullException(e));
                mHandler.sendMessage(msg);
            }
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
    }

    private void editText(final File file) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            try {
                TextEditor.data = FileUtil.readFile(file);
                Intent intent = new Intent(PerezReverseKillerMain.this, TextEditor.class);
                intent.putExtra(TextEditor.PLUGIN, "TextEditor");
                startActivityForResult(intent, ActResConstant.list_item_details);
            } catch(Exception e) {
                Message msg = new Message();
                msg.what = SHOWMESSAGE;
                msg.obj = new DialogMsg(getString(R.string.open_text_failed), RealFuncUtil.getFullException(e));
                mHandler.sendMessage(msg);
            }
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
    }

    private void editAxml(final File file) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            try {
                TextEditor.data = FileUtil.readFile(file);
                Intent intent = new Intent(PerezReverseKillerMain.this, TextEditor.class);
                intent.putExtra(TextEditor.PLUGIN, "AXmlEditor");
                startActivityForResult(intent, ActResConstant.list_item_details);
            } catch(Exception e) {
                Message msg = new Message();
                msg.what = SHOWMESSAGE;
                msg.obj = new DialogMsg(getString(R.string.open_axml_failed), RealFuncUtil.getFullException(e));
                mHandler.sendMessage(msg);
            }
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
    }

    private void openDexFile(final File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.tips));
        builder.setMessage(getString(R.string.dex_instruction));
        builder.setPositiveButton(getString(R.string.tojar), (dialog, whichButton) -> new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            try {
                String dest_file = file.toString() + "_dex2jar.jar";
                RealFuncUtil.DexTrans(file.toString(), dest_file);
                showToast(false, getString(R.string.dex2jar_success));
            } catch(Exception e) {
                Message msg = new Message();
                msg.what = SHOWMESSAGE;
                msg.obj = new DialogMsg(getString(R.string.dex2jar_fail), RealFuncUtil.getFullException(e));
                mHandler.sendMessage(msg);
            }
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start());
        builder.setNeutralButton(getString(R.string.editdex), (dialog, whichButton) -> new Thread(() -> {
            try {
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                ClassListActivity.dexFile = new DexFile(file);
                Intent intent = new Intent(PerezReverseKillerMain.this, ClassListActivity.class);
                startActivityForResult(intent, ActResConstant.list_item_details);
            } catch(Exception e) {
                Message msg = new Message();
                msg.what = SHOWMESSAGE;
                msg.obj = new DialogMsg(getString(R.string.open_dex_error), RealFuncUtil.getFullException(e));
                mHandler.sendMessage(msg);
            }
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start());
        builder.setNegativeButton(getString(R.string.disasm_dex), (dialog, which) -> new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            boolean DISDEX_SUC = BakSmaliFunc.DoBaksmali(file.toString(), file.toString().substring(0, file.toString().length() - 4) + "_baksmali");
            if(!DISDEX_SUC)
                showToast(false, getString(R.string.disdex_success));
            else
                showToast(false, getString(R.string.disdex_fail));
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start());
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
        case ActResConstant.list_item_details:
            switch(resultCode) {
            case ActResConstant.text_editor:
                renameAndWrite();
                break;
            case ActResConstant.zip_list_item:
                mAdapter.notifyDataSetInvalidated();
                break;
            }
            break;
        }
    }

    private void renameAndWrite() {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            FileOutputStream out = null;
            try {
                FileUtil.rename(mCurrent, mCurrent.getName() + ".bak");
                out = new FileOutputStream(mCurrent.getAbsolutePath());
                out.write(TextEditor.data);
            } catch(Exception ignored) {
            } finally {
                if(out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                TextEditor.data = null;
                System.gc();
            }
            Message msg = new Message();
            msg.what = TOAST;
            msg.obj = mCurrent.getName() + getString(R.string.saved);
            mHandler.sendMessage(msg);
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
    }

    public void zipAlign(final File file) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            if(Features.isZipAligned(file.toString())) {
                showToast(false, getString(R.string.zip_has_aligned));
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
                return;
            }
            boolean b = Features.ZipAlign(file.toString(), addSuffixToExtension(file.toString(), "aligned"));
            if(b)
                showToast(false, getString(R.string.zipa_success));
            else
                showToast(false, getString(R.string.zipa_fail));
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(initialized) {
            mAdapter.notifyDataSetChanged();
            initRealtimeCallForwardingListener();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();
        menu.add(Menu.NONE, R.string.add_folder, Menu.NONE, R.string.add_folder);
        menu.add(Menu.NONE, R.string.howto_rank, Menu.NONE, R.string.howto_rank);
        if(mClipboard != null)
            menu.add(Menu.NONE, R.string.paste, Menu.NONE, R.string.paste);
        menu.add(Menu.NONE, R.string.dumpdex, Menu.NONE, R.string.dumpdex);
        menu.add(Menu.NONE, R.string.scan_qrcode, Menu.NONE, R.string.scan_qrcode);
        menu.add(Menu.NONE, R.string.httpcaptool, Menu.NONE, R.string.httpcaptool);

        MenuItem cfiItem = menu.add(Menu.NONE, R.string.call_forwarding, Menu.NONE, R.string.call_forwarding);
        cfiItem.setCheckable(true);
        cfiItem.setChecked(isCallForwardingEnabled);

        menu.add(Menu.NONE, R.string.palette_app, Menu.NONE, R.string.palette_app);
        menu.add(Menu.NONE, R.string.about, Menu.NONE, R.string.about);
        if(!mSelectMod)
            menu.add(Menu.NONE, R.string.action_exit, Menu.NONE, R.string.action_exit);
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterRealtimeCallForwardingListener();
        mAdapter.unregisterDataSetObserver(dataSetObserver);
        dataSetObserver = null;
    }

    public void clearAll() {
        mCurrent = null;
        mClipboard = null;
        mCurrentDir = null;
        mCut = false;
        pos = null;
        System.gc();
    }

    private void showGenJniDialog(final File targetClass, final File outputDir) {
        final String[] languages = new String[]{"C/C++", "Rust", "Go"};
        final boolean[] checkedItems = new boolean[]{true, false, false};

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.gen_jni_lang_sel)
                .setMultiChoiceItems(languages, checkedItems, (dialogInterface, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                    AlertDialog d = (AlertDialog) dialogInterface;
                    android.widget.Button okBtn = d.getButton(AlertDialog.BUTTON_POSITIVE);
                    if(okBtn != null) {
                        boolean hasSelection = checkedItems[0] || checkedItems[1] || checkedItems[2];
                        okBtn.setEnabled(hasSelection);
                    }
                })
                .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                    List<TargetLanguage> targetLangs = new ArrayList<>();
                    if(checkedItems[0]) targetLangs.add(TargetLanguage.C);
                    if(checkedItems[1]) targetLangs.add(TargetLanguage.RUST);
                    if(checkedItems[2]) targetLangs.add(TargetLanguage.GO);

                    if(targetLangs.isEmpty()) {
                        showToast(true, getString(R.string.gen_jni_lang_sel));
                        return;
                    }

                    new Thread(() -> {
                        mHandler.sendEmptyMessage(SHOWPROGRESS);
                        try {
                            JavahTask task = new JavahTask();
                            task.setOutputDir(outputDir);
                            task.addClass(targetClass);
                            task.setTargetLanguages(targetLangs.toArray(new TargetLanguage[0]));
                            task.run();
                            showToast(false, getString(R.string.gen_jni_success));
                            runOnUiThread(() -> mAdapter.notifyDataSetInvalidated());
                        } catch (Exception e) {
                            runOnUiThread(() -> RealFuncUtil.showDlgMsg(PerezReverseKillerMain.this,
                                    getString(R.string.gen_jni_failed), RealFuncUtil.getFullException(e), null));
                        } finally {
                            mHandler.sendEmptyMessage(DISMISSPROGRESS);
                        }
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button okBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if(okBtn != null) {
                boolean hasSelection = checkedItems[0] || checkedItems[1] || checkedItems[2];
                okBtn.setEnabled(hasSelection);
            }
        });

        dialog.show();
    }

    private void makeChoice() {
        RankPrefUtil rpf = new RankPrefUtil(this);
        final String[] criteria = getResources().getStringArray(R.array.rank_criteria);
        int initialIndex = getIndexForCriteria(rpf.GetWhich());

        // Root container to prevent overflow on small or landscape screens
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        int paddingH = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        int paddingV = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        container.setPadding(paddingH, paddingV, paddingH, paddingV);

        // Build the RadioGroup dynamically from string-array criteria
        RadioGroup radioGroup = new RadioGroup(this);
        for (int i = 0; i < criteria.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(i); // Assign criteria index directly as the View ID
            rb.setText(criteria[i]);
            radioGroup.addView(rb);
            if (i == initialIndex) {
                rb.setChecked(true);
            }
        }
        container.addView(radioGroup);

        // Visual divider between criteria and options
        View divider = new View(this);
        int dividerHeight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());
        int dividerMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dividerHeight);
        dividerParams.setMargins(0, dividerMargin, 0, dividerMargin);
        divider.setLayoutParams(dividerParams);

        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.listDivider, typedValue, true)) {
            divider.setBackgroundResource(typedValue.resourceId);
        } else {
            divider.setBackgroundColor(0x1F000000); // 12% black fallback
        }
        container.addView(divider);

        // Bottom reverse sorting checkbox
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(R.string.rev_sort);
        checkBox.setChecked(rpf.GetReverse());
        container.addView(checkBox);

        scrollView.addView(container);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.howto_rank);
        builder.setView(scrollView);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            int selectedIndex = radioGroup.getCheckedRadioButtonId();
            switch (selectedIndex) {
                case 0:
                    rpf.SetByName();
                    break;
                case 1:
                    rpf.SetByType();
                    break;
                case 2:
                    rpf.SetByDate();
                    break;
                case 3:
                    rpf.SetBySize();
                    break;
            }

            // Apply reverse sort preference and refresh data in a single pass
            rpf.SetReverse(checkBox.isChecked());
            mAdapter.notifyDataSetInvalidated();
        });

        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());

        builder.create().show();
    }

    private void dumpDex() {
        LayoutInflater factory = LayoutInflater.from(this);
        final View view = factory.inflate(R.layout.editbox_layout, null);
        final EditText edit = view.findViewById(R.id.editText1);
        edit.setHint(R.string.dumpdex_hint);
        AlertDialog alg = new AlertDialog.Builder(PerezReverseKillerMain.this)
                .setTitle(R.string.dumpdex_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> new Thread(() -> {
                    String clz = edit.getText().toString();
                    if(clz.trim().isEmpty()) {
                        showToast(false, getString(R.string.dumpdex_err));
                    } else Features.dumpDex(21, clz);
                }).start()).setNegativeButton(android.R.string.cancel, null).create();
        Objects.requireNonNull(alg.getWindow()).clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        alg.show();
    }

    private void initRealtimeCallForwardingListener() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if(tm == null) return;

        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                TelephonyCallbackApi31.registerRealtime(tm, ContextCompat.getMainExecutor(this), this::onCallForwardingStatusChanged);
            } else {
                if(mRealtimePhoneStateListener == null) {
                    mRealtimePhoneStateListener = new PhoneStateListener() {
                        @Override
                        @SuppressWarnings("deprecation")
                        public void onCallForwardingIndicatorChanged(boolean cfi) {
                            onCallForwardingStatusChanged(cfi);
                        }
                    };
                }
                tm.listen(mRealtimePhoneStateListener, PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR);
            }
        } catch(SecurityException e) {
            Log.w(TAG, "Cannot register call forwarding listener: " + e.getMessage());
        }
    }

    private void unregisterRealtimeCallForwardingListener() {
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if(tm == null) return;
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                TelephonyCallbackApi31.unregisterRealtime(tm);
            } else {
                if(mRealtimePhoneStateListener != null) {
                    tm.listen(mRealtimePhoneStateListener, PhoneStateListener.LISTEN_NONE);
                    mRealtimePhoneStateListener = null;
                }
            }
        } catch(Exception ignored) {}
    }

    private void onCallForwardingStatusChanged(boolean cfi) {
        if(isCallForwardingEnabled != cfi) {
            isCallForwardingEnabled = cfi;
            runOnUiThread(this::invalidateOptionsMenu);
        }
    }

    private void toggleCallF() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if(telephonyManager == null) return;
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            showToast(true, getString(R.string.lack_perms));
            return;
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            TelephonyCallbackApi31.queryAndExecute(telephonyManager, ContextCompat.getMainExecutor(this), forwardingState -> {
                onCallForwardingStatusChanged(forwardingState);
                dialCallForwarding(forwardingState);
            });
        } else {
            telephonyManager.listen(new PhoneStateListener() {
                @Override
                @SuppressWarnings("deprecation")
                public void onCallForwardingIndicatorChanged(boolean isEnabled) {
                    telephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
                    onCallForwardingStatusChanged(isEnabled);
                    dialCallForwarding(isEnabled);
                }
            }, PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR);
        }
    }

    private void dialCallForwarding(boolean enabled) {
        Intent callIntent;
        if(enabled) {
            callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:%23%2321%23"));
        } else {
            String number = "13800000000";
            callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:**21*" + number + "%23"));
        }
        try {
            startActivity(callIntent);
        } catch(SecurityException e) {
            showToast(true, "Permission CALL_PHONE required");
        }
    }

    private int getIndexForCriteria(String criteria) {
        switch (criteria) {
            case "type":
                return 1;
            case "date":
                return 2;
            case "size":
                return 3;
            default:
                return 0;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch(itemId) {
            case R.string.add_folder:
                newFolder();
                break;
            case R.string.howto_rank:
                makeChoice();
                break;
            case R.string.paste:
                pasteFile();
                break;
            case R.string.dumpdex:
                dumpDex();
                break;
            case R.string.scan_qrcode: {
                startActivity(new Intent(PerezReverseKillerMain.this, QRCodeCamActivity.class));
                break;
            }
            case R.string.httpcaptool: {
                startActivity(new Intent(PerezReverseKillerMain.this, NDGAct.class));
                break;
            }
            case R.string.call_forwarding: {
                toggleCallF();
                break;
            }
            case R.string.palette_app:
                startActivity(new Intent(PerezReverseKillerMain.this, SketchActivity.class));
                break;
            case R.string.about:
                showAbout();
                break;
            case R.string.action_exit:
                finish();
                clearAll();
                System.exit(0);
                break;
        }
        return true;
    }

    private void digitalSignApk(final File file) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            try {
                String out = file.getAbsolutePath();
                out = addSuffixToExtension(out, "signed");
                ApkSignerTool.sign(this, file, out);
                Message msg = new Message();
                msg.what = TOAST;
                msg.obj = out + getString(R.string.signed_success);
                mHandler.sendMessage(msg);
            } catch(Exception e) {
                Message msg = new Message();
                msg.what = SHOWMESSAGE;
                msg.obj = new DialogMsg(getString(R.string.signed_failed), RealFuncUtil.getFullException(e));
                mHandler.sendMessage(msg);
            }
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
    }

    private void extractAll(final File file) {
        String absName = file.getAbsolutePath();
        int i = absName.indexOf('.');
        if (i != -1) {
            absName = absName.substring(0, i);
        }
        absName += "_extracted";

        final EditText srcName = new EditText(this);
        srcName.setText(absName);

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.extract_path);
        alert.setView(srcName);
        alert.setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
            String src = srcName.getText().toString();
            if (src.isEmpty()) {
                showToast(true, getString(R.string.extract_path_empty));
                return;
            }
            new Thread(() -> {
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                try {
                    ZipExtract.unzipAll(new ZipFile(file), new File(src));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
            }).start();
        });
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

    private void dialogMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(mCurrent.getName());
        builder.setItems(R.array.dialog_menu, (dialog, which) -> {
            switch(which) {
            case 0:
                viewCurrent();
                break;
            case 1:
                editText(mCurrent);
                break;
            case 2:
                delete(mCurrent);
                break;
            case 3:
                Rename(mCurrent);
                break;
            case 4:
                addCopy(mCurrent);
                break;
            case 5:
                addCut(mCurrent);
                break;
            case 6:
                showPermissions();
                break;
            }
        });
        builder.show();
    }

    private void setPermBit(int perms, int bit, int id) {
        CheckBox ck = mPermissionDialog.findViewById(id);
        ck.setChecked(((perms >> bit) & 1) == 1);
    }

    private int getPermBit(int bit, int id) {
        CheckBox ck = mPermissionDialog.findViewById(id);
        return (ck.isChecked()) ? (1 << bit) : 0;
    }

    public void showPermissions() {
        mPermissionDialog.setTitle(mCurrent.getName());
        int perms = FileUtil.getPermissions(mCurrent);
        setPermBit(perms, 8, R.id.ckOwnRead);
        setPermBit(perms, 7, R.id.ckOwnWrite);
        setPermBit(perms, 6, R.id.ckOwnExec);
        setPermBit(perms, 5, R.id.ckGrpRead);
        setPermBit(perms, 4, R.id.ckGrpWrite);
        setPermBit(perms, 3, R.id.ckGrpExec);
        setPermBit(perms, 2, R.id.ckOthRead);
        setPermBit(perms, 1, R.id.ckOthWrite);
        setPermBit(perms, 0, R.id.ckOthExec);
        mPermissionDialog.show();
    }

    private void setPermissions() {
        mPermissionDialog.hide();
        int perms = getPermBit(8, R.id.ckOwnRead) | getPermBit(7, R.id.ckOwnWrite) | getPermBit(6, R.id.ckOwnExec)
                    | getPermBit(5, R.id.ckGrpRead) | getPermBit(4, R.id.ckGrpWrite) | getPermBit(3, R.id.ckGrpExec)
                    | getPermBit(2, R.id.ckOthRead) | getPermBit(1, R.id.ckOthWrite) | getPermBit(0, R.id.ckOthExec);
        try {
            FileUtil.chmod(mCurrent, perms);
            mAdapter.notifyDataSetChanged();
        } catch (ErrnoException e) {
            RealFuncUtil.showDlgMsg(this, getString(R.string.set_perm_failed), RealFuncUtil.getFullException(e), null);
        }
    }

    private void viewCurrent() {
        String fn = mCurrent.toString();
        if(fn.substring(fn.lastIndexOf(".")).equals(".apk")) {
            RealFuncUtil.installProcess(mCurrent, this);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", mCurrent);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        String mime = URLConnection.guessContentTypeFromName(uri.toString());
        if(mime != null) {
            if("text/x-java".equals(mime) || "text/xml".equals(mime))
                intent.setDataAndType(uri, "text/plain");
            else
                intent.setDataAndType(uri, mime);
        } else intent.setDataAndType(uri, "*/*");
        startActivity(intent);
    }

    private void addCopy(File file) {
        mClipboard = file;
        showToast(true, getString(R.string.copy_to) + file.getName());
        mCut = false;
    }

    private void addCut(File file) {
        mClipboard = file;
        showToast(true, getString(R.string.cut_to) + file.getName());
        mCut = true;
    }

    private void pasteFile() {
        String message = "";
        if(mClipboard == null) {
            RealFuncUtil.showDlgMsg(this, getString(R.string.copy_failed), getString(R.string.copy_nothing), null);
            return;
        }
        final File destination = new File(mCurrentDir, mClipboard.getName());
        if(destination.exists())
            message = String.format(getString(R.string.copy_message), destination.getName());
        if(!message.isEmpty()) {
            RealFuncUtil.showDlgMsg(this, getString(R.string.over_write), message, (dialog, which) -> {
                if(which == AlertDialog.BUTTON_POSITIVE)
                    performPasteFile(mClipboard, destination);
            });
        } else performPasteFile(mClipboard, destination);
    }

    protected void performPasteFile(final File source, final File destination) {
        if(source.isDirectory())
            RealFuncUtil.showDlgMsg(this, getString(R.string.copy_failed), getString(R.string.copy_exist), null);
        else {
            new Thread(() -> {
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                try {
                    FileUtil.copyFile(source, destination);
                    if(mCut) source.delete();
                } catch(Exception e) {
                    e.printStackTrace();
                }
                mClipboard = null;
                Message msg = new Message();
                msg.what = TOAST;
                msg.obj = destination.getName() + getString(R.string.copied);
                mHandler.sendMessage(msg);
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
            }).start();
        }
    }

    public void ExtractRar(final File name) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            int results = Features.ExtractAllRAR(name.toString(),
                                                 name.toString().substring(0, name.toString().length() - 4) + "_extracted");
            if(results == 0)
                showToast(false, getString(R.string.extract_rar_success));
            else if(results == -1601)
                showToast(false, getString(R.string.rar_native_error));
            else
                showToast(false, getString(R.string.failed_to_extract_rar));
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
    }

    public void OatToDex(final File name) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            String str = name.toString();
            boolean suc = Features.Oat2Dex(str);
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
            if(suc)
                showToast(false, getString(R.string.oat2dex_success));
            else
                showToast(false, getString(R.string.oat2dex_fail));
        }).start();
    }

    private void PELF(File name) {
        if(Features.isValidElf(name.toString())) {
            Intent i = new Intent(this, ElfActivity.class);
            i.putExtra("FILE_NAME", name.toString());
            startActivity(i);
            this.mAdapter.notifyDataSetInvalidated();
        } else showToast(true, getString(R.string.invalid_elf));
    }

    public void ConOdex(final File name) {
        if(Features.isValidElf(name.toString()))
            OatToDex(name);
        else {
            new Thread(() -> {
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                boolean success2 = Features.Odex2Dex(name.toString(),
                                                     name.toString().substring(0, name.toString().length() - 5) + "_converted.dex");
                if(success2)
                    showToast(false, getString(R.string.odex2dex_success));
                else
                    showToast(false, getString(R.string.odex2dex_fail));
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
            }).start();
        }
    }

    public void buildSmali(final File name) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            String[] args = {"a", name.toString(), "-o", name + "_smali.dex",
                    "-j", String.format("%d", Runtime.getRuntime().availableProcessors())};
            Main.main(args);
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(getString(R.string.wait));
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        return dialog;
    }

    public void showToast(boolean isMain, final String msg) {
        if(isMain) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        else this.runOnUiThread(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show());
    }

    @SuppressLint("MissingPermission")
    public void SystemInfo() {
        StringBuilder info = new StringBuilder();

        new Thread(() -> {
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            List<String> sis = RealFuncUtil.getSelfInstallSource(this);
            long deviceId = RealFuncUtil.getDeviceId(this).hashCode();
            info.append("Phone model: ").append(Build.MODEL).append("\n");
            info.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
            info.append("Android version: ").append(Build.VERSION.RELEASE).append("\n");
            info.append("Android SDK code: ").append(Build.VERSION.SDK_INT).append("\n");
            info.append("CPU variant: ").append(Build.CPU_ABI).append(" / ").append(Build.CPU_ABI2).append("\n");
            info.append("Hardware serial code: ").append(Build.SERIAL).append("\n");
            info.append("Hardware name: ").append(Build.HARDWARE).append("\n");
            info.append("Baseband version: ").append(Build.getRadioVersion()).append("\n");
            info.append("BootLoader version: ").append(Build.BOOTLOADER).append("\n");
            info.append("System ID: ").append(androidId).append("\n");
            info.append("Device ID: ").append(deviceId).append("\n");
            info.append("App ZIP signature: ").append(RealFuncUtil.md5(RealFuncUtil.getZipSig(this))).append("\n");
            info.append("App SVC signature: ").append(RealFuncUtil.md5(RealFuncUtil.getSvcSig(this))).append("\n");
            if(!sis.isEmpty()) {
                info.append("Installation Info:").append("\n");
                info.append("\t").append("Who installed me: ").append(sis.get(0)).append("\n");
                if(sis.size() > 1) {
                    info.append("\t").append("Who initiated my installation: ").append(sis.get(1)).append("\n");
                    info.append("\t").append("My package origin: ").append(sis.get(2)).append("\n");
                    if(sis.size() > 3) info.append("\t").append("Update owner package: ").append(sis.get(3)).append("\n");
                }
            }
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.system_info));
                builder.setMessage(info.toString());
                builder.setNeutralButton(android.R.string.ok, null);
                builder.show();
            });
        }).start();
    }


    public String readAboutContent() {
        StringBuilder changelog = new StringBuilder();
        Locale locale = getResources().getConfiguration().locale;
        String language = locale.getLanguage();
        String filename;
        switch (language) {
            case "es":
                filename = "changelog-es.txt";
                break;
            case "fr":
                filename = "changelog-fr.txt";
                break;
            case "zh":
                filename = "changelog-zh.txt";
                break;
            case "ja":
                filename = "changelog-ja.txt";
                break;
            case "en":
            default:
                filename = "changelog-en.txt";
                break;
        }
        try {
            InputStream inputStream = getAssets().open(filename);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                changelog.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return changelog.toString();
    }

    public void showAbout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.android);
        String title = getString(R.string.app_name);
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
            if(pi.versionName != null) title += " " + pi.versionName;
        } catch(Exception e) {
            e.printStackTrace();
        }
        builder.setTitle(title);
        builder.setMessage(readAboutContent());
        builder.setNeutralButton(android.R.string.ok, null);
        builder.setPositiveButton(R.string.system_info, (dialog, which) -> {
            dialog.dismiss();
            SystemInfo();
        });
        builder.show();
    }

    private void delete(final File file) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.delete);
        alert.setMessage(String.format(getString(R.string.is_delete), file.getName()));
        alert.setPositiveButton(R.string.btn_yes, (dialog, whichButton) -> new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            FileUtil.delete(file);
            mFileList.remove(file);
            Message msg = new Message();
            msg.what = TOAST;
            msg.obj = String.format("%s %s", file.getName(), getString(R.string.deleted));
            mHandler.sendMessage(msg);
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start());
        alert.setNegativeButton(R.string.btn_no, null);
        alert.show();
    }

    private void newFolder() {
        final EditText folderName = new EditText(this);
        folderName.setHint(R.string.folder_name);
        final AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.add_folder);
        alert.setView(folderName);
        alert.setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
            String name = folderName.getText().toString();
            if(name.isEmpty()) {
                showToast(true, getString(R.string.directory_empty));
                return;
            } else {
                for(File f : mFileList) {
                    if(f.getName().equals(name)) {
                        showToast(true, String.format(getString(R.string.directory_exists), name));
                        return;
                    }
                }
            }
            File dir = new File(mCurrentDir, name);
            if(!dir.mkdirs())
                showToast(true, String.format(getString(R.string.directory_cannot_create), name));
            else
                showToast(true, String.format(getString(R.string.directory_created), name));
            mAdapter.notifyDataSetInvalidated();
        });
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

    private void Rename(final File file) {
        final EditText newName = new EditText(this);
        newName.setText(file.getName());
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.rename);
        alert.setView(newName);
        alert.setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
            String name = newName.getText().toString();
            if(name.isEmpty()) {
                showToast(true, getString(R.string.name_empty));
                return;
            } else {
                for(File f : mFileList) {
                    if(f.getName().equals(name)) {
                        showToast(true, String.format(getString(R.string.file_exists), name));
                        return;
                    }
                }
            }
            if(!FileUtil.rename(file, name))
                showToast(true, String.format(getString(R.string.cannot_rename), file.getPath()));
            mAdapter.notifyDataSetInvalidated();
        });
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

}
