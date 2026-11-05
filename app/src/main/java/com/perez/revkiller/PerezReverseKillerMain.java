package com.perez.revkiller;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
import com.perez.util.ExifUtil;
import com.perez.revkiller.FileManagerPreferenceActivity.FileManagerSettingsFragment;
import com.perez.revkiller.adapter.FileListAdapter;
import com.perez.signer.ApkSigning;
import com.perez.util.FileUtil;
import com.perez.util.HashUtil;
import com.perez.util.RankPrefUtil;
import com.perez.util.RealFuncUtil;
import com.perez.util.TelephonyCallbackApi31;
import com.perez.util.VineflowerJarDecompiler;
import com.perez.util.ZipExtract;
import com.perez.xml2axml.func.FuncMain;
import com.perez.util.J2DMain;
import com.perez.util.BakSmaliFunc;

import org.jb.dexlib.DexFile;
import org.jf.smali.Main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

public class PerezReverseKillerMain extends AppCompatActivity {
    public final static String ENTRYPATH = "ZipEntry";
    public final static String SELECTEDMOD = "selected_mod";
    public final static String TAG = "PerezReverseKillerMain";

    public static final int SHOWPROGRESS = 1;
    public static final int DISMISSPROGRESS = 2;
    public static final int TOAST = 3;
    public static final int SHOWMESSAGE = 4;

    public static final int RQ_PERMISSION = 0xffee;

    public static final String[] pmList1 = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE};
    public static final String[] pmList2 = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public boolean initialized = false;

    private Stack<Integer> pos = new Stack<>();
    public static List<File> mFileList;
    private FileListAdapter mAdapter;
    private boolean m_isFilePickingMode = false;
    private boolean m_isPreparedToBuildSmali = false;
    private File mCurrentDir;
    private File mCurrent;
    private ListView fileList;
    private SwipeRefreshLayout srLayout;
    private ProgressDialog mGeneralProgressDialog, mFileListLoadingDialog;

    public int position;
    private static boolean mCut;
    private static File mClipboard;
    private Dialog mPermissionDialog;
    private volatile boolean isCallForwardingEnabled = false;
    private PhoneStateListener mRealtimePhoneStateListener;
    private final ExecutorService mFileScanExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger mCurrentScanToken = new AtomicInteger(0);
    private boolean isUpdatingPermUi = false;

    private Dialog mTimestampDialog;
    private boolean isUpdatingTimestampUi = false;
    private final SimpleDateFormat mTimestampSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$");
    private int mCurrentSecond = 0;

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
                showProgressDialog(mGeneralProgressDialog, getString(R.string.plz_wait));
                break;
            case DISMISSPROGRESS:
                mAdapter.notifyDataSetInvalidated();
                dismissProgressDialog(mGeneralProgressDialog);
                break;
            case SHOWMESSAGE:
                if(msg.obj instanceof DialogMsg) {
                    DialogMsg dm = (DialogMsg) msg.obj;
                    RealFuncUtil.showDlgMsg(PerezReverseKillerMain.this, dm.title, dm.msg, null);
                }
                break;
            case TOAST:
                showToast(true, msg.obj.toString());
                break;
            }
        }
    };

    private void showProgressDialog(ProgressDialog which, String message) {
        if (which == null || isFinishing() || isDestroyed()) return;
        which.setMessage(message);
        which.setIndeterminate(true);
        which.setCancelable(false);
        if (!which.isShowing()) {
            which.show();
        }
    }

    private void dismissProgressDialog(ProgressDialog which) {
        if (which != null && which.isShowing()) {
            try {
                which.dismiss();
            } catch (Exception ignored) {}
        }
    }

    public void showToast(boolean isMain, final String msg) {
        if(isMain) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        else this.runOnUiThread(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show());
    }

    private DataSetObserver dataSetObserver = new DataSetObserver() {
        @Override
        public void onInvalidated() {
        loadFileListAsync("", -1);
        }
    };

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if(m_isFilePickingMode)
            return;
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.setHeaderTitle(R.string.options);
        File file;
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            file = (File) fileList.getItemAtPosition(info.position);
        } catch(ClassCastException e) {
            Log.e(TAG, "Bad menuInfo" + e);
            return;
        }
        if(!file.isDirectory()) menu.add(Menu.NONE, R.string.view, Menu.NONE, R.string.view);
        String ext_name = FileUtil.getFileExtension(file.getName());
        menu.add(Menu.NONE, R.string.delete, Menu.NONE, R.string.delete);
        menu.add(Menu.NONE, R.string.action_rename, Menu.NONE, R.string.action_rename);
        if(RealFuncUtil.isZip(file)) {
            menu.add(Menu.NONE, R.string.sign_apk, Menu.NONE, R.string.sign_apk);
            menu.add(Menu.NONE, R.string.extract_all, Menu.NONE, R.string.extract_all);
            menu.add(Menu.NONE, R.string.zipalign, Menu.NONE, R.string.zipalign);
        }
        menu.add(Menu.NONE, R.string.action_copy, Menu.NONE, R.string.action_copy);
        menu.add(Menu.NONE, R.string.action_cut, Menu.NONE, R.string.action_cut);
        if(mClipboard != null) menu.add(Menu.NONE, R.string.action_paste, Menu.NONE, R.string.action_paste);
        if (!file.isDirectory()) {
            menu.add(Menu.NONE, R.string.share, Menu.NONE, R.string.share);
            menu.add(Menu.NONE, R.string.checksum, Menu.NONE, R.string.checksum);
        }
        menu.add(Menu.NONE, R.string.permission, Menu.NONE, R.string.permission);
        menu.add(Menu.NONE, R.string.modify_timestamp, Menu.NONE, R.string.modify_timestamp);
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
            if(info == null) return false;
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
                case R.string.sign_apk:
                    digitalSignApk(mCurrent);
                    return true;
                case R.string.action_rename:
                    rename(mCurrent);
                    return true;
                case R.string.action_copy:
                    addCopy(mCurrent);
                    return true;
                case R.string.action_cut:
                    addCut(mCurrent);
                    return true;
                case R.string.action_paste:
                    pasteFile();
                    return true;
                case R.string.permission:
                    showPermissions();
                    return true;
                case R.string.share:
                    shareFile(mCurrent);
                    return true;
                case R.string.modify_timestamp:
                    showModTimestampDlg();
                    return true;
                case R.string.checksum:
                    showChecksumDialog(mCurrent);
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
                            ExifUtil.deleteEXIF(mCurrent.getPath(), mCurrent.getPath());
                            showToast(false, getString(R.string.remove_exif_success));
                            runOnUiThread(() -> mAdapter.notifyDataSetInvalidated());
                        } catch (Exception e) {
                            runOnUiThread(() -> RealFuncUtil.showDlgMsg(PerezReverseKillerMain.this,
                                    getString(R.string.remove_exif_failed), RealFuncUtil.getFullException(e), null));
                        }
                    }).start();
                    return true;
                case R.string.str_jpg2png:
                    convJpgPng(mCurrent.getPath(), true);
                    return true;
                case R.string.str_png2jpg:
                    convJpgPng(mCurrent.getPath(), false);
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
    private void handleBackPressAction() {
        if (!initialized || mCurrentDir == null) {
            finish();
            return;
        }

        File parent = mCurrentDir.getParentFile();

        // Only navigate up if the parent directory actually exists and is readable
        if (FileUtil.canListFiles(parent)) {
            mCurrentDir = parent;
            int targetPos = !pos.empty() ? pos.pop() : 0;
            loadFileListAsync("", targetPos);
            return;
        }

        // Parent is inaccessible without root (e.g. reached /storage/emulated/0)
        if (m_isFilePickingMode) finish();
    }

    private boolean hasAllRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return RealFuncUtil.hasPermissions(this, pmList1) && Environment.isExternalStorageManager();
        } else {
            return RealFuncUtil.hasPermissions(this, pmList2);
        }
    }

    private void requestManageAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, RQ_PERMISSION);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, RQ_PERMISSION);
            }
        }
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!RealFuncUtil.hasPermissions(this, pmList1)) {
                ActivityCompat.requestPermissions(this, pmList1, RQ_PERMISSION);
            } else if (!Environment.isExternalStorageManager()) {
                requestManageAllFilesAccess();
            } else {
                initData();
            }
        } else {
            if (!RealFuncUtil.hasPermissions(this, pmList2)) {
                ActivityCompat.requestPermissions(this, pmList2, RQ_PERMISSION);
            } else {
                initData();
            }
        }
    }

    private void showPermissionDeniedWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.warning);
        builder.setMessage(R.string.lack_perms);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> Process.killProcess(Process.myPid()));
        builder.setCancelable(false);
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RQ_PERMISSION && grantResults.length > 0) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    requestManageAllFilesAccess();
                } else {
                    initData();
                }
            } else {
                showPermissionDeniedWarning();
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.listact);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressAction();
            }
        });

        if (savedInstanceState != null) {
            String dirPath = savedInstanceState.getString("SAVED_CURRENT_DIR");
            if (dirPath != null) {
                mCurrentDir = new File(dirPath);
            }
            String curPath = savedInstanceState.getString("SAVED_CURRENT_FILE");
            if (curPath != null) {
                mCurrent = new File(curPath);
            }
            position = savedInstanceState.getInt("SAVED_LIST_POSITION", 0);
            ArrayList<Integer> savedPos = savedInstanceState.getIntegerArrayList("SAVED_POS_STACK");
            if (savedPos != null) {
                pos = new Stack<>();
                pos.addAll(savedPos);
            }
            m_isFilePickingMode = savedInstanceState.getBoolean("SAVED_SELECT_MOD", false);
        } else {
            m_isFilePickingMode = getIntent().getBooleanExtra(SELECTEDMOD, false);
        }

        initViews();
        checkAndRequestPermissions();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mCurrentDir != null) {
            outState.putString("SAVED_CURRENT_DIR", mCurrentDir.getAbsolutePath());
        }
        if (mCurrent != null) {
            outState.putString("SAVED_CURRENT_FILE", mCurrent.getAbsolutePath());
        }
        int scrollPos = (fileList != null) ? fileList.getFirstVisiblePosition() : position;
        outState.putInt("SAVED_LIST_POSITION", scrollPos);
        if (pos != null && !pos.isEmpty()) {
            outState.putIntegerArrayList("SAVED_POS_STACK", new ArrayList<>(pos));
        }
        outState.putBoolean("SAVED_SELECT_MOD", m_isFilePickingMode);
    }

    private void initViews() {
        if (fileList != null) {
            return;
        }
        fileList = findViewById(R.id.file_list_view);
        mGeneralProgressDialog = new ProgressDialog(this);
        mFileListLoadingDialog = new ProgressDialog(this);
        mAdapter = new FileListAdapter(getApplication(), true);
        mAdapter.registerDataSetObserver(dataSetObserver);
        registerForContextMenu(fileList);
        fileList.setAdapter(mAdapter);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        fileList.setOnItemClickListener((parent, view, position, id) -> {
            final File file = (File) parent.getItemAtPosition(position);
            PerezReverseKillerMain.this.position = position;
            mCurrent = file;
            if (file.isDirectory()) {
                if (file.toString().endsWith("_baksmali")) {
                    m_isPreparedToBuildSmali = true;
                    new AlertDialog.Builder(PerezReverseKillerMain.this).setTitle(getString(R.string.prompt)).
                            setMessage(getString(R.string.smali_instruction)).setPositiveButton(getString(R.string.build_smali), (arg0, arg1) -> {
                                buildSmali(file);
                                m_isPreparedToBuildSmali = false;
                            }).setNeutralButton(getString(R.string.explore_dir), (arg0, arg1) -> {
                                if (!FileUtil.canListFiles(file)) {
                                    m_isPreparedToBuildSmali = false;
                                    return;
                                }
                                mCurrentDir = file;
                                pos.push(parent.getFirstVisiblePosition());
                                loadFileListAsync("", 0);
                                m_isPreparedToBuildSmali = false;
                            }).show();
                } else {
                    if (!FileUtil.canListFiles(file)) return;
                    mCurrentDir = file;
                    pos.push(parent.getFirstVisiblePosition());
                    loadFileListAsync("", 0);
                    return;
                }
            }
            if (m_isFilePickingMode) {
                m_isFilePickingMode = false;
                resultFileToZipEditor(file);
                return;
            }
            if (RealFuncUtil.isZip(file))
                openZipLike(file);
            else {
                Intent intent;
                switch (FileUtil.getFileExtension(file.getName())) {
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
                    case "jpeg":
                    case "png":
                    case "bmp":
                    case "gif":
                    case "webp":
                        intent = new Intent(PerezReverseKillerMain.this, HugeImageViewerActivity.class);
                        intent.setData(Uri.parse(file.toString()));
                        startActivity(intent);
                        break;
                    case "rar":
                        extractAll(file);
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
                        if (!m_isPreparedToBuildSmali) dialogMenu();
                        break;
                }
            }
        });

        srLayout = findViewById(R.id.swipeRefresh);
        srLayout.setOnRefreshListener(() -> {
            mAdapter.notifyDataSetInvalidated();
            srLayout.setRefreshing(false);
        });
    }

    private void initData() {
        if (initialized) {
            return;
        }
        if (mCurrentDir == null) {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
                mCurrentDir = Environment.getExternalStorageDirectory();
            else
                mCurrentDir = Environment.getRootDirectory();
        }
        loadFileListAsync("", position);
        enableRealtimeCFListener(true);
        initialized = true;
    }

    private int getPermBit(int bit, int id) {
        CheckBox ck = mPermissionDialog.findViewById(id);
        return (ck != null && ck.isChecked()) ? (1 << bit) : 0;
    }

    private void setPermBit(int perms, int bit, int id) {
        CheckBox ck = mPermissionDialog.findViewById(id);
        if (ck != null) {
            ck.setChecked(((perms >> bit) & 1) == 1);
        }
    }

    private void initPermissionDialog() {
        if (mPermissionDialog != null) return;

        mPermissionDialog = new Dialog(this);
        mPermissionDialog.setContentView(R.layout.permissions);
        mPermissionDialog.findViewById(R.id.btnOk).setOnClickListener(v -> setPermissions());
        mPermissionDialog.findViewById(R.id.btnCancel).setOnClickListener(v -> mPermissionDialog.dismiss());

        // Listen for changes to the CheckBox: immediately reflect on the octal input field and symbol preview
        android.widget.CompoundButton.OnCheckedChangeListener boxListener = (buttonView, isChecked) -> {
            if (isUpdatingPermUi) return;
            int mode = collectPermissionsFromUi();
            applyPermissionsToUi(mode, true);
        };

        int[] checkIds = {
                R.id.ckSpecialSuid, R.id.ckSpecialSgid, R.id.ckSpecialSticky,
                R.id.ckOwnRead, R.id.ckOwnWrite, R.id.ckOwnExec,
                R.id.ckGrpRead, R.id.ckGrpWrite, R.id.ckGrpExec,
                R.id.ckOthRead, R.id.ckOthWrite, R.id.ckOthExec
        };
        for (int id : checkIds) {
            CheckBox cb = mPermissionDialog.findViewById(id);
            if (cb != null) {
                cb.setOnCheckedChangeListener(boxListener);
            }
        }

        // Listen for changes to the input field (convert octal to a numeric value)
        // immediately reflect on the checkbox matrix and symbol preview
        EditText etOctal = mPermissionDialog.findViewById(R.id.etPermOctal);
        if (etOctal != null) {
            etOctal.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    if (isUpdatingPermUi) return;
                    String text = s.toString().trim();
                    if (text.isEmpty()) return;
                    try {
                        int parsedMode = Integer.parseInt(text, 8);
                        if (parsedMode >= 0 && parsedMode <= FileUtil.PERM_MASK) {
                            applyPermissionsToUi(parsedMode, false);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            });
        }
    }

    /**
     * Calculate the current 12-bit permission mode based on the 12 checkboxes in the dialog box
     */
    private int collectPermissionsFromUi() {
        return getPermBit(11, R.id.ckSpecialSuid)
                | getPermBit(10, R.id.ckSpecialSgid)
                | getPermBit(9, R.id.ckSpecialSticky)
                | getPermBit(8, R.id.ckOwnRead)
                | getPermBit(7, R.id.ckOwnWrite)
                | getPermBit(6, R.id.ckOwnExec)
                | getPermBit(5, R.id.ckGrpRead)
                | getPermBit(4, R.id.ckGrpWrite)
                | getPermBit(3, R.id.ckGrpExec)
                | getPermBit(2, R.id.ckOthRead)
                | getPermBit(1, R.id.ckOthWrite)
                | getPermBit(0, R.id.ckOthExec);
    }

    /**
     * Synchronously update the given 12-bit value across the CheckBox, input field, and symbolic string
     */
    private void applyPermissionsToUi(int perms, boolean updateEditText) {
        isUpdatingPermUi = true;
        try {
            setPermBit(perms, 11, R.id.ckSpecialSuid);
            setPermBit(perms, 10, R.id.ckSpecialSgid);
            setPermBit(perms, 9, R.id.ckSpecialSticky);
            setPermBit(perms, 8, R.id.ckOwnRead);
            setPermBit(perms, 7, R.id.ckOwnWrite);
            setPermBit(perms, 6, R.id.ckOwnExec);
            setPermBit(perms, 5, R.id.ckGrpRead);
            setPermBit(perms, 4, R.id.ckGrpWrite);
            setPermBit(perms, 3, R.id.ckGrpExec);
            setPermBit(perms, 2, R.id.ckOthRead);
            setPermBit(perms, 1, R.id.ckOthWrite);
            setPermBit(perms, 0, R.id.ckOthExec);

            EditText etOctal = mPermissionDialog.findViewById(R.id.etPermOctal);
            if (updateEditText && etOctal != null) {
                etOctal.setText(String.format(Locale.ROOT, "%04o", perms & FileUtil.PERM_MASK));
                etOctal.setSelection(etOctal.getText().length());
            }

            TextView tvSymbolic = mPermissionDialog.findViewById(R.id.tvPermSymbolic);
            if (tvSymbolic != null) {
                tvSymbolic.setText(FileUtil.formatPermissionsSymbolic(perms));
            }
        } finally {
            isUpdatingPermUi = false;
        }
    }

    public void showPermissions() {
        if (mCurrent == null || !mCurrent.exists()) return;

        initPermissionDialog();

        mPermissionDialog.setTitle(mCurrent.getName());
        int currentPerms = FileUtil.getPermissions(mCurrent);
        applyPermissionsToUi(currentPerms, true);
        mPermissionDialog.show();
    }

    private void setPermissions() {
        if (mPermissionDialog != null) {
            mPermissionDialog.dismiss();
        }
        int perms = collectPermissionsFromUi();
        try {
            FileUtil.chmod(mCurrent, perms);
            mAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            RealFuncUtil.showDlgMsg(this, getString(R.string.set_perm_failed), RealFuncUtil.getFullException(e), null);
        }
    }

    private interface HashComputer {
        String compute(File file) throws Exception;
    }

    private static class ChecksumRow {
        final String name;
        final HashComputer computer;
        EditText etHash;
        Button btnCopy;
        ColorStateList defaultTextColor;

        ChecksumRow(String name, HashComputer computer) {
            this.name = name;
            this.computer = computer;
        }
    }

    private void showChecksumDialog(final File file) {
        if (file == null || !file.exists() || file.isDirectory()) return;

        final List<ChecksumRow> rows = new ArrayList<>();
        rows.add(new ChecksumRow("CRC32", HashUtil::crc32));
        rows.add(new ChecksumRow("CRC64", HashUtil::crc64));
        rows.add(new ChecksumRow("MD5", HashUtil::md5));
        rows.add(new ChecksumRow("SHA-1", HashUtil::sha1));
        rows.add(new ChecksumRow("SHA-256", HashUtil::sha256));
        rows.add(new ChecksumRow("SHA-512", HashUtil::sha512));
        rows.add(new ChecksumRow("SHA3-256", HashUtil::sha3_256));
        rows.add(new ChecksumRow("BLAKE2sp", HashUtil::blake2sp));
        rows.add(new ChecksumRow("xxHash64", HashUtil::xxh64));

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_checksum, null);
        LinearLayout container = dialogView.findViewById(R.id.llChecksumContainer);

        for (ChecksumRow row : rows) {
            View itemView = inflater.inflate(R.layout.item_checksum_row, container, false);
            TextView tvName = itemView.findViewById(R.id.tvAlgorithmName);
            EditText etHash = itemView.findViewById(R.id.etHashValue);
            Button btnCopy = itemView.findViewById(R.id.btnCopy);

            tvName.setText(row.name);
            row.etHash = etHash;
            row.btnCopy = btnCopy;

            row.defaultTextColor = etHash.getTextColors();

            etHash.setKeyListener(null);
            etHash.setTextIsSelectable(true);

            etHash.setText(R.string.plz_wait);
            etHash.setTextColor(Color.GRAY);
            btnCopy.setEnabled(false);

            container.addView(itemView);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.checksum) + ": " + file.getName())
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        final AtomicBoolean isDismissed = new AtomicBoolean(false);
        dialog.setOnDismissListener(d -> isDismissed.set(true));
        dialog.show();

        // Use a single-threaded sequential read: This avoids I/O contention and stuttering caused by 9 parallel
        // threads simultaneously reading from the mobile device's flash memory, and allows subsequent
        // algorithms to fully benefit from the operating system's page cache acceleration.
        new Thread(() -> {
            for (ChecksumRow row : rows) {
                if (isDismissed.get() || isFinishing() || isDestroyed()) {
                    return;
                }

                String result;
                boolean success;
                try {
                    result = row.computer.compute(file);
                    success = true;
                } catch (Exception e) {
                    result = getString(R.string.checksum_failed);
                    success = false;
                }

                final String finalResult = result;
                final boolean finalSuccess = success;

                // After calculating each item, immediately call back to the main thread to update that row of the UI
                runOnUiThread(() -> {
                    if (isDismissed.get() || isFinishing() || isDestroyed()) {
                        return;
                    }

                    row.etHash.setText(finalResult);
                    if (row.defaultTextColor != null) {
                        row.etHash.setTextColor(row.defaultTextColor);
                    }

                    if (finalSuccess) {
                        row.btnCopy.setEnabled(true);
                        row.btnCopy.setOnClickListener(v -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                ClipData clip = ClipData.newPlainText(row.name, finalResult);
                                cm.setPrimaryClip(clip);
                                showToast(true, getString(R.string.copied_tips));
                            }
                        });
                    }
                });
            }
        }).start();
    }

    @SuppressWarnings("deprecation")
    private int getTimePickerHour(TimePicker tp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return tp.getHour();
        } else {
            return tp.getCurrentHour();
        }
    }

    @SuppressWarnings("deprecation")
    private void setTimePickerHour(TimePicker tp, int hour) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tp.setHour(hour);
        } else {
            tp.setCurrentHour(hour);
        }
    }

    @SuppressWarnings("deprecation")
    private int getTimePickerMinute(TimePicker tp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return tp.getMinute();
        } else {
            return tp.getCurrentMinute();
        }
    }

    @SuppressWarnings("deprecation")
    private void setTimePickerMinute(TimePicker tp, int minute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tp.setMinute(minute);
        } else {
            tp.setCurrentMinute(minute);
        }
    }

    private void initTimestampDialog() {
        if (mTimestampDialog != null) return;

        mTimestampDialog = new Dialog(this);
        mTimestampDialog.setContentView(R.layout.dialog_modify_timestamp);

        final EditText etTimestamp = mTimestampDialog.findViewById(R.id.etTimestamp);
        final DatePicker datePicker = mTimestampDialog.findViewById(R.id.datePicker);
        final TimePicker timePicker = mTimestampDialog.findViewById(R.id.timePicker);
        final Button btnOk = mTimestampDialog.findViewById(R.id.btnOk);
        final Button btnCancel = mTimestampDialog.findViewById(R.id.btnCancel);
        final Button btnCurrentTime = mTimestampDialog.findViewById(R.id.btnCurrentTime);

        timePicker.setIs24HourView(true);
        mTimestampSdf.setLenient(false);

        timePicker.setOnTimeChangedListener((view, hourOfDay, minute) -> {
            if (isUpdatingTimestampUi) return;
            syncPickersToEditText();
        });

        // DatePicker value change listener (pass the listener in init)
        Calendar now = Calendar.getInstance();
        datePicker.init(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH),
                (view, year, monthOfYear, dayOfMonth) -> {
                    if (isUpdatingTimestampUi) return;
                    syncPickersToEditText();
                });

        // EditText text change monitoring: Linked to DatePicker and TimePicker, and format validation
        etTimestamp.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (isUpdatingTimestampUi) return;
                String text = s.toString().trim();

                if (TIMESTAMP_PATTERN.matcher(text).matches()) {
                    try {
                        Date date = mTimestampSdf.parse(text);
                        if (date != null) {
                            // Correct format: Clear errors and sync to DatePicker/TimePicker
                            etTimestamp.setBackgroundResource(R.drawable.edit_text_normal_border);
                            etTimestamp.setError(null);
                            btnOk.setEnabled(true);
                            applyDateToPickers(date);
                            return;
                        }
                    } catch (ParseException ignored) {}
                }

                // Incorrect format
                etTimestamp.setBackgroundResource(R.drawable.edit_text_error_border);
                etTimestamp.setError(getString(R.string.invalid_timestamp_format));
                btnOk.setEnabled(false);
            }
        });

        btnCancel.setOnClickListener(v -> mTimestampDialog.dismiss());

        btnCurrentTime.setOnClickListener(v -> {
            Date currentDate = new Date();
            applyTimestampToUi(currentDate, true);
        });

        btnOk.setOnClickListener(v -> {
            String text = etTimestamp.getText().toString().trim();
            try {
                Date targetDate = mTimestampSdf.parse(text);
                if (targetDate != null) {
                    performModifyTimestamp(targetDate);
                }
            } catch (ParseException e) {
                etTimestamp.setBackgroundResource(R.drawable.edit_text_error_border);
                etTimestamp.setError(getString(R.string.invalid_timestamp_format));
            }
        });
    }

    /**
     * Read the values from DatePicker and TimePicker, format them, and then synchronize them to the EditText
     */
    private void syncPickersToEditText() {
        if (mTimestampDialog == null) return;
        DatePicker datePicker = mTimestampDialog.findViewById(R.id.datePicker);
        TimePicker timePicker = mTimestampDialog.findViewById(R.id.timePicker);
        EditText etTimestamp = mTimestampDialog.findViewById(R.id.etTimestamp);
        Button btnOk = mTimestampDialog.findViewById(R.id.btnOk);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, datePicker.getYear());
        cal.set(Calendar.MONTH, datePicker.getMonth());
        cal.set(Calendar.DAY_OF_MONTH, datePicker.getDayOfMonth());
        cal.set(Calendar.HOUR_OF_DAY, getTimePickerHour(timePicker));
        cal.set(Calendar.MINUTE, getTimePickerMinute(timePicker));
        cal.set(Calendar.SECOND, mCurrentSecond);

        isUpdatingTimestampUi = true;
        try {
            etTimestamp.setText(mTimestampSdf.format(cal.getTime()));
            etTimestamp.setBackgroundResource(R.drawable.edit_text_normal_border);
            etTimestamp.setError(null);
            if (btnOk != null) btnOk.setEnabled(true);
        } finally {
            isUpdatingTimestampUi = false;
        }
    }

    /**
     * Synchronize the specified Date object to both DatePicker and TimePicker
     */
    private void applyDateToPickers(Date date) {
        if (mTimestampDialog == null) return;
        DatePicker datePicker = mTimestampDialog.findViewById(R.id.datePicker);
        TimePicker timePicker = mTimestampDialog.findViewById(R.id.timePicker);

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        mCurrentSecond = cal.get(Calendar.SECOND);

        isUpdatingTimestampUi = true;
        try {
            datePicker.updateDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            setTimePickerHour(timePicker, cal.get(Calendar.HOUR_OF_DAY));
            setTimePickerMinute(timePicker, cal.get(Calendar.MINUTE));
        } finally {
            isUpdatingTimestampUi = false;
        }
    }

    /**
     * Synchronously update the UI of the entire dialogue box (including EditText and selection controls)
     */
    private void applyTimestampToUi(Date date, boolean updateEditText) {
        if (mTimestampDialog == null) return;
        EditText etTimestamp = mTimestampDialog.findViewById(R.id.etTimestamp);
        Button btnOk = mTimestampDialog.findViewById(R.id.btnOk);

        applyDateToPickers(date);

        if (updateEditText && etTimestamp != null) {
            isUpdatingTimestampUi = true;
            try {
                etTimestamp.setText(mTimestampSdf.format(date));
                etTimestamp.setBackgroundResource(R.drawable.edit_text_normal_border);
                etTimestamp.setError(null);
                if (btnOk != null) btnOk.setEnabled(true);
            } finally {
                isUpdatingTimestampUi = false;
            }
        }
    }

    public void showModTimestampDlg() {
        if (mCurrent == null || !mCurrent.exists()) return;

        initTimestampDialog();
        mTimestampDialog.setTitle(mCurrent.getName());

        // Take the initial value from the file's current last modified time
        Date fileDate = new Date(mCurrent.lastModified());
        applyTimestampToUi(fileDate, true);

        mTimestampDialog.show();
    }

    /**
     * Perform file timestamp modification operation: Obtain the file descriptor through ParcelFileDescriptor and call JNI
     */
    private void performModifyTimestamp(final Date targetDate) {
        if (mCurrent == null || !mCurrent.exists()) return;

        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            ParcelFileDescriptor pfd = null;
            try {
                int openMode = mCurrent.isDirectory() ? ParcelFileDescriptor.MODE_READ_ONLY : ParcelFileDescriptor.MODE_READ_WRITE;
                pfd = ParcelFileDescriptor.open(mCurrent, openMode);

                int fd = pfd.detachFd();

                Features.StatusMsg status = Features.modifyTimestamp(fd, targetDate);

                runOnUiThread(() -> {
                    if (status != null && status.isSuccess()) {
                        showToast(true, getString(R.string.modify_timestamp_success));
                        if (mTimestampDialog != null && mTimestampDialog.isShowing()) {
                            mTimestampDialog.dismiss();
                        }
                        mAdapter.notifyDataSetInvalidated();
                    } else {
                        String errMsg = (status != null && status.getStaMsg() != null)
                                ? status.getStaMsg()
                                : getString(R.string.modify_timestamp_failed);
                        RealFuncUtil.showDlgMsg(PerezReverseKillerMain.this, getString(R.string.modify_timestamp_failed), errMsg, null);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> RealFuncUtil.showDlgMsg(PerezReverseKillerMain.this,
                        getString(R.string.modify_timestamp_failed), RealFuncUtil.getFullException(e), null));
            } finally {
                if (pfd != null) {
                    try {
                        pfd.close();
                    } catch (Exception ignored) {}
                }
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
            }
        }).start();
    }

    /**
     * Asynchronously load files in the current directory to completely avoid blocking the main thread and causing lag
     * when there are too many files in the current directory.
     * @param query Filter String
     * @param targetPosition The position that the ListView needs to restore or navigate to after
     *                       loading is complete (if less than 0, it won't change).
     */
    private void loadFileListAsync(final String query, final int targetPosition) {
        if (mCurrentDir == null) {
            return;
        }
        final File scanDir = mCurrentDir;
        final int token = mCurrentScanToken.incrementAndGet();

        showProgressDialog(mFileListLoadingDialog, getString(R.string.loading_please_wait));

        // Delegate directory scanning, filtering, and sorting to background threads
        mFileScanExecutor.execute(() -> {
            File[] files = scanDir.listFiles();

            // If directory is inaccessible (permission denied), abort and keep current list intact
            if (files == null) {
                runOnUiThread(() -> {
                    if (token != mCurrentScanToken.get()) {
                        return;
                    }
                    dismissProgressDialog(mFileListLoadingDialog);
                    if (srLayout != null && srLayout.isRefreshing()) {
                        srLayout.setRefreshing(false);
                    }
                });
                return;
            }

            final List<File> work = new ArrayList<>();
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PerezReverseKillerMain.this.getApplicationContext());
            boolean showHidden = sp.getBoolean("pref_key_show_hidden", false);
            for (File file : files) {
                if (!showHidden && file.getName().startsWith(".")) {
                    continue;
                }
                if (query == null || query.isEmpty()) {
                    work.add(file);
                } else if (file.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                    work.add(file);
                }
            }

            RankPrefUtil rpf = new RankPrefUtil(PerezReverseKillerMain.this);
            switch (rpf.GetWhich()) {
                case "1":
                    Collections.sort(work, RankPrefUtil.sortByType);
                    break;
                case "2":
                    Collections.sort(work, RankPrefUtil.sortByDate);
                    break;
                case "3":
                    Collections.sort(work, RankPrefUtil.sortBySize);
                    break;
                case "0":
                default:
                    Collections.sort(work, RankPrefUtil.sortByName);
                    break;
            }
            if (rpf.GetReverse()) {
                Collections.reverse(work);
            }

            // Only insert ".." if the parent directory exists and can be listed
            File parent = scanDir.getParentFile();
            if (FileUtil.canListFiles(parent)) {
                work.add(0, new File(Objects.requireNonNull(scanDir.getParent())) {
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

            // Call-back the main thread to update UI after the completion of background procedure
            runOnUiThread(() -> {
                // If a new directory switch request is triggered during the loading process, discard the old result
                if (token != mCurrentScanToken.get()) {
                    return;
                }
                mFileList = work;
                setTitle(scanDir.getPath());
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
                if (targetPosition >= 0 && fileList != null) {
                    fileList.setSelection(targetPosition);
                }
                if (srLayout != null && srLayout.isRefreshing()) {
                    srLayout.setRefreshing(false);
                }
                dismissProgressDialog(mFileListLoadingDialog);
            });
        });
    }

    private void resultFileToZipEditor(File file) {
        Intent intent = getIntent();
        intent.putExtra(ENTRYPATH, file.getAbsolutePath());
        setResult(ActResConstant.add_entry, intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RQ_PERMISSION) {
            if (hasAllRequiredPermissions()) {
                initData();
            } else {
                showPermissionDeniedWarning();
            }
            return;
        }

        switch (requestCode) {
            case ActResConstant.list_item_details:
                switch (resultCode) {
                    case ActResConstant.text_editor:
                        if (mCurrent != null) {
                            showToast(true, mCurrent.getName() + " " + getString(R.string.saved));
                        }
                        mAdapter.notifyDataSetInvalidated();
                        break;
                    case ActResConstant.zip_list_item:
                        mAdapter.notifyDataSetInvalidated();
                        break;
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!initialized) {
            if (hasAllRequiredPermissions()) {
                initData();
            }
        } else {
            int currentPos = (fileList != null) ? fileList.getFirstVisiblePosition() : -1;
            loadFileListAsync("", currentPos);
            enableRealtimeCFListener(true);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissProgressDialog(mFileListLoadingDialog);
        enableRealtimeCFListener(false);
        if (mAdapter != null && dataSetObserver != null) {
            mAdapter.unregisterDataSetObserver(dataSetObserver);
        }
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

    private void onCallForwardingStatusChanged(boolean cfi) {
        if(isCallForwardingEnabled != cfi) {
            isCallForwardingEnabled = cfi;
            runOnUiThread(this::invalidateOptionsMenu);
        }
    }

    private void enableRealtimeCFListener(boolean enable) {
        if(RealFuncUtil.hasPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE})) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if(tm == null) return;

            try {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if(enable) TelephonyCallbackApi31.registerRealtime(tm, ContextCompat.getMainExecutor(this), this::onCallForwardingStatusChanged);
                    else TelephonyCallbackApi31.unregisterRealtime(tm);
                } else {
                    if(enable) {
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
                    } else {
                        if(mRealtimePhoneStateListener != null) {
                            tm.listen(mRealtimePhoneStateListener, PhoneStateListener.LISTEN_NONE);
                            mRealtimePhoneStateListener = null;
                        }
                    }
                }
            } catch(SecurityException e) {
                Log.w(TAG, "Cannot register call forwarding listener: " + e.getMessage());
            }
        }
    }

    private void toggleCFState() {
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if(tm == null) return;
        if(!RealFuncUtil.hasPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE})) {
            showToast(true, getString(R.string.lack_perms));
            return;
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            TelephonyCallbackApi31.queryAndExecute(tm, ContextCompat.getMainExecutor(this), forwardingState -> {
                onCallForwardingStatusChanged(forwardingState);
                dialCF(forwardingState);
            });
        } else {
            tm.listen(new PhoneStateListener() {
                @Override
                @SuppressWarnings("deprecation")
                public void onCallForwardingIndicatorChanged(boolean isEnabled) {
                    tm.listen(this, PhoneStateListener.LISTEN_NONE);
                    onCallForwardingStatusChanged(isEnabled);
                    dialCF(isEnabled);
                }
            }, PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR);
        }
    }

    private void dialCF(boolean enabled) {
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

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();
        menu.add(Menu.NONE, R.string.add_folder, Menu.NONE, R.string.add_folder);
        if(mClipboard != null) menu.add(Menu.NONE, R.string.action_paste, Menu.NONE, R.string.action_paste);
        menu.add(Menu.NONE, R.string.dumpdex, Menu.NONE, R.string.dumpdex);
        menu.add(Menu.NONE, R.string.scan_qrcode, Menu.NONE, R.string.scan_qrcode);
        menu.add(Menu.NONE, R.string.httpcaptool, Menu.NONE, R.string.httpcaptool);
        menu.add(Menu.NONE, R.string.purgador, Menu.NONE, R.string.purgador);

        MenuItem cfiItem = menu.add(Menu.NONE, R.string.call_forwarding, Menu.NONE, R.string.call_forwarding);
        cfiItem.setCheckable(true);
        cfiItem.setChecked(isCallForwardingEnabled);

        menu.add(Menu.NONE, R.string.palette_app, Menu.NONE, R.string.palette_app);
        menu.add(Menu.NONE, R.string.pref_title_settings, Menu.NONE, R.string.pref_title_settings);
        menu.add(Menu.NONE, R.string.about, Menu.NONE, R.string.about);
        if(!m_isFilePickingMode) menu.add(Menu.NONE, R.string.action_exit, Menu.NONE, R.string.action_exit);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch(itemId) {
            case R.string.add_folder:
                newFolder();
                break;
            case R.string.pref_title_settings:
                startActivity(new Intent(this, FileManagerPreferenceActivity.class));
                break;
            case R.string.action_paste:
                pasteFile();
                break;
            case R.string.dumpdex:
                dumpDex();
                break;
            case R.string.scan_qrcode: {
                startActivity(new Intent(this, QRCodeCamActivity.class));
                break;
            }
            case R.string.httpcaptool: {
                startActivity(new Intent(this, NDGAct.class));
                break;
            }
            case R.string.purgador: {
                startActivity(new Intent(this, PurgarActivity.class));
                break;
            }
            case R.string.call_forwarding: {
                toggleCFState();
                break;
            }
            case R.string.palette_app:
                startActivity(new Intent(this, SketchActivity.class));
                break;
            case R.string.about:
                showAbout();
                break;
            case R.string.action_exit:
                finish();
                clearAll();
                System.exit(0);
                break;
            case android.R.id.home:
                getOnBackPressedDispatcher().onBackPressed();
                break;
        }
        return true;
    }

    private void digitalSignApk(final File file) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            try {
                File ofile = new File(FileUtil.addFnSuffix(file.getAbsolutePath(), "signed"));
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PerezReverseKillerMain.this.getApplicationContext());
                boolean v1 = sp.getBoolean(FileManagerSettingsFragment.KEY_SIGN_V1, true);
                boolean v2 = sp.getBoolean(FileManagerSettingsFragment.KEY_SIGN_V2, true);
                boolean v3 = sp.getBoolean(FileManagerSettingsFragment.KEY_SIGN_V3, true);
                boolean v4 = sp.getBoolean(FileManagerSettingsFragment.KEY_SIGN_V4, false);
                if (sp.getBoolean(FileManagerSettingsFragment.KEY_USE_DEBUG_KEYSTORE, true)) {
                    ApkSigning.signDebugKey(PerezReverseKillerMain.this, file, ofile, v1, v2, v3, v4);
                } else {
                    String ksPath = sp.getString(FileManagerSettingsFragment.KEY_KEYSTORE_PATH, "");
                    String ksPw = sp.getString(FileManagerSettingsFragment.KEY_KEYSTORE_PASSWORD, "");
                    String alias = sp.getString(FileManagerSettingsFragment.KEY_KEYSTORE_ALIAS, "");
                    String aliasPw = sp.getString(FileManagerSettingsFragment.KEY_KEYSTORE_ALIAS_PASSWORD, "");
                    if(!ksPath.isEmpty() && !alias.isEmpty() && !aliasPw.isEmpty() && !ksPw.isEmpty()) {
                        InputStream is = ApkSigning.openKeystoreStream(PerezReverseKillerMain.this, ksPath);
                        ApkSigning.signApk(is, ksPw, alias, aliasPw, file, ofile, v1, v2, v3, v4);
                    } else {
                        showToast(false, getString(R.string.ks_conf_incomplete));
                        mHandler.sendEmptyMessage(DISMISSPROGRESS);
                        return;
                    }
                }
                if(sp.getBoolean(FileManagerSettingsFragment.KEY_ENABLE_ZIPALIGN, true)) {
                    File tmpAlignFile = new File(FileUtil.addFnSuffix(ofile.getAbsolutePath(), "tmp"));
                    if(!ApkSigning.performZipAlign(ofile, tmpAlignFile)) {
                        showToast(false, getString(R.string.zip_align_fail));
                        mHandler.sendEmptyMessage(DISMISSPROGRESS);
                        return;
                    } else {
                        ofile.delete();
                        tmpAlignFile.renameTo(ofile);
                    }
                }
                showToast(false, String.format(getString(R.string.signed_success), ofile.getName()));
            } catch(Exception e) {
                Message msg = new Message();
                msg.what = SHOWMESSAGE;
                msg.obj = new DialogMsg(getString(R.string.signed_failed), RealFuncUtil.getFullException(e));
                mHandler.sendMessage(msg);
            }
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
    }

    public void zipAlign(final File file) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            if(ApkSigning.verifyZipAlign(file)) {
                showToast(false, getString(R.string.zip_has_aligned));
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
            } else {
                boolean b;
                String errMsg = "";
                try {
                    b = ApkSigning.performZipAlign(file, new File(FileUtil.addFnSuffix(file.toString(), "aligned")));
                } catch (Exception e) {
                    b = false;
                    errMsg = RealFuncUtil.getFullException(e);
                }
                if(b) showToast(false, getString(R.string.zip_align_success));
                else {
                    if(errMsg.isEmpty()) showToast(false, getString(R.string.zip_align_fail));
                    else {
                        String finalErrMsg = errMsg;
                        runOnUiThread(() -> RealFuncUtil.showDlgMsg(this, getString(R.string.zip_align_fail), finalErrMsg, null));
                    }
                }
            }
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
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
                rename(mCurrent);
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
            case 7:
                showModTimestampDlg();
                break;
            case 8:
                showChecksumDialog(mCurrent);
                break;
            }
        });
        builder.show();
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

    private void delete(final File file) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Runnable delTask = () -> new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            FileUtil.delete(file);
            mFileList.remove(file);
            Message msg = new Message();
            msg.what = TOAST;
            msg.obj = String.format(getString(R.string.deleted), file.getName());
            mHandler.sendMessage(msg);
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
        if(sp.getBoolean("pref_key_confirm_delete", true)) {
            RealFuncUtil.showDlgMsg(this, getString(R.string.delete), String.format(getString(R.string.is_delete), file.getName()),
                    (dlg, which) -> {
                        if(which == AlertDialog.BUTTON_POSITIVE) delTask.run();
                    });
        } else delTask.run();
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

    private void rename(final File file) {
        final EditText newName = new EditText(this);
        newName.setText(file.getName());
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.action_rename);
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
                msg.obj = String.format(getString(R.string.file_copied), destination.getName());
                mHandler.sendMessage(msg);
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
            }).start();
        }
    }

    private void shareFile(final File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            showToast(true, getString(R.string.share_failed));
            return;
        }

        try {
            // Use the same FileProvider as viewCurrent to obtain the content URI
            Uri uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);

            // Infer the MIME type but if the type is unknown, fall back to `application/octet-stream`
            String mime = URLConnection.guessContentTypeFromName(file.getName());
            if (mime == null || mime.isEmpty()) {
                mime = "application/octet-stream";
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mime);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            // Grant the recipient temporary read access
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(shareIntent, getString(R.string.share));
            startActivity(chooser);
        } catch (Exception e) {
            RealFuncUtil.showDlgMsg(this, getString(R.string.share_failed), RealFuncUtil.getFullException(e), null);
        }
    }

    private void extractAll(final File file) {
        String absName = FileUtil.getFileNameNoEx(file.getAbsolutePath()) + "_extracted";
        final String extName = FileUtil.getFileExtension(file.getName());

        final EditText dstName = new EditText(this);
        dstName.setText(absName);

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.extract_path);
        alert.setView(dstName);
        alert.setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
            String dst = dstName.getText().toString();
            if (dst.isEmpty()) {
                showToast(true, getString(R.string.extract_path_empty));
                return;
            }
            new Thread(() -> {
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                switch (extName) {
                    case "zip":
                    case "apk":
                    case "jar":
                        try {
                            ZipExtract.unzipAll(new ZipFile(file), new File(dst));
                            showToast(false, getString(R.string.extract_success));
                        } catch (Exception e) {
                            showToast(false, getString(R.string.failed_to_extract) + ": " + e);
                        }
                        break;
                    case "rar":
                        int results = Features.ExtractAllRAR(file.toString(), dst);
                        if(results == 0)
                            showToast(false, getString(R.string.extract_success));
                        else if(results == -1601)
                            showToast(false, getString(R.string.rar_native_error));
                        else
                            showToast(false, getString(R.string.failed_to_extract));
                        break;
                    default:
                        break;
                }
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
            }).start();
        });
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

    private void convJpgPng(String sourcePath, boolean direction) {
        new Thread(() -> {
            if(direction) { // jpg -> png
                if(FileUtil.convertToPng(sourcePath, FileUtil.getFileNameNoEx(sourcePath) + ".png"))
                    showToast(false, getString(R.string.img_conv_suc));
                else showToast(false, getString(R.string.img_conv_failed));
                runOnUiThread(() -> mAdapter.notifyDataSetInvalidated());
            } else { // png -> jpg
                boolean hasAlpha = FileUtil.hasAlphaChannel(sourcePath);

                Runnable startConversionTask = () -> new Thread(() -> {
                    String destPath = FileUtil.getFileNameNoEx(sourcePath) + ".jpg";
                    boolean success = FileUtil.convertToJpg(sourcePath, destPath);
                    showToast(false, getString(success ? R.string.img_conv_suc : R.string.img_conv_failed));
                    runOnUiThread(() -> mAdapter.notifyDataSetInvalidated());
                }).start();

                if (hasAlpha) {
                    runOnUiThread(() -> RealFuncUtil.showDlgMsg(
                            this,
                            getString(R.string.warning),
                            getString(R.string.png_has_alpha),
                            (dlg, which) -> {
                                if (which == AlertDialog.BUTTON_POSITIVE) {
                                    startConversionTask.run();
                                }
                            }
                    ));
                } else {
                    startConversionTask.run();
                }
            }
        }).start();
    }

    private void procXml(final File file) {
        String message;
        Runnable positiveAction, neutralAction;

        if (FuncMain.isBinAXML(file.toString())) {
            message = getString(R.string.axml_instruction);
            positiveAction = () -> {
                boolean success = false;
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                try {
                    FuncMain.decode(file.toString(), FileUtil.addFnSuffix(file.toString(), "_dec"));
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
                            FileUtil.addFnSuffix(file.toString(), "_comp"));
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

        AlertDialog.Builder builder = new AlertDialog.Builder(PerezReverseKillerMain.this);
        builder.setTitle(getString(R.string.prompt));
        builder.setMessage(message);
        builder.setPositiveButton(R.string.btn_dec, (dialog, which) -> positiveAction.run());
        builder.setNeutralButton(R.string.btn_edit_axml, (dialog, which) -> neutralAction.run());
        builder.show();
    }

    public void openZipLike(final File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.prompt));

        if (file.toString().endsWith(".jar") && RealFuncUtil.isStandardJAR(file.toString())) {
            builder.setMessage(getString(R.string.jar_instruction));
            builder.setPositiveButton(getString(R.string.todex), (dialog, whichButton) -> new Thread(() -> {
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                boolean JAR2DEX_SUC = false;
                try {
                    JAR2DEX_SUC = J2DMain.JarToDex(file.toString(), FileUtil.getFileNameNoEx(file.toString()) + "_converted.dex");
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
                            new File(file.getParent() + "/" + FileUtil.getFileNameNoEx(file.getName()) + ".zip"));
                    showToast(false, getString(R.string.djar_success));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
            }).start());
            builder.setNeutralButton(getString(R.string.explore_jar), (dialog, whichButton) -> {
                Intent intent = new Intent(PerezReverseKillerMain.this, ZipManagerMain.class);
                intent.putExtra("filePath", file.getAbsolutePath());
                startActivityForResult(intent, ActResConstant.list_item_details);
            });
            builder.show();
        } else if (file.toString().endsWith(".apk")) {
            builder.setMessage(getString(R.string.apk_instruction));
            builder.setPositiveButton(getString(R.string.open_apk), (dialog, whichButton) -> {
                Intent intent = new Intent(PerezReverseKillerMain.this, ZipManagerMain.class);
                intent.putExtra("filePath", file.getAbsolutePath());
                startActivityForResult(intent, ActResConstant.list_item_details);
            });
            builder.setNegativeButton(getString(R.string.decompile_javainapk), (dialog, which) -> {
                Intent intent = new Intent(PerezReverseKillerMain.this, PackageActivity.class);
                intent.putExtra("filePath", file.toString());
                PerezReverseKillerMain.this.startActivity(intent);
            });
            builder.show();
        } else {
            Intent intent = new Intent(this, ZipManagerMain.class);
            intent.putExtra("filePath", file.getAbsolutePath());
            startActivityForResult(intent, ActResConstant.list_item_details);
        }
    }

    private void editArsc(final File file) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            try {
                Intent it = new Intent(PerezReverseKillerMain.this, ArscActivity.class);
                it.putExtra("filePath", file.toString());
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
                Intent intent = new Intent(PerezReverseKillerMain.this, TextEditor.class);
                intent.putExtra("filePath", file.getAbsolutePath());
                intent.setData(Uri.fromFile(file));
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
                Intent intent = new Intent(PerezReverseKillerMain.this, TextEditor.class);
                intent.putExtra("filePath", file.getAbsolutePath());
                intent.setData(Uri.fromFile(file));
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
        builder.setTitle(getString(R.string.prompt));
        builder.setMessage(getString(R.string.dex_instruction));
        builder.setPositiveButton(getString(R.string.tojar), (dialog, whichButton) -> new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            try {
                String dest_file = FileUtil.getFileNameNoEx(file.toString()) + "_dex2jar.jar";
                RealFuncUtil.DexTrans(file, new File(dest_file));
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
            boolean DISDEX_SUC = BakSmaliFunc.DoBaksmali(file.toString(), FileUtil.getFileNameNoEx(file.toString()) + "_baksmali");
            if(!DISDEX_SUC)
                showToast(false, getString(R.string.disdex_success));
            else
                showToast(false, getString(R.string.disdex_fail));
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start());
        builder.show();
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
            i.putExtra("filePath", name.toString());
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
                boolean success2 = Features.Odex2Dex(name.toString(), FileUtil.getFileNameNoEx(name.toString()) + "_converted.dex");
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
                    "-j", String.valueOf(Runtime.getRuntime().availableProcessors())};
            Main.main(args);
            mHandler.sendEmptyMessage(DISMISSPROGRESS);
        }).start();
    }

    public String readAboutContent() {
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
            return FileUtil.readFromAssetsTxt(this, filename);
        } catch (IOException ignored) {
        }
        return "";
    }

    public void showAbout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.android);
        String title = getString(R.string.app_name);
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
            if(pi.versionName != null) title += " " + pi.versionName;
        } catch(Exception ignored) {
        }
        builder.setTitle(title);
        builder.setMessage(readAboutContent());
        builder.setNeutralButton(android.R.string.ok, null);
        builder.setPositiveButton(R.string.system_info, (dialog, which) -> {
            dialog.dismiss();
            new Thread(() -> {
                String info = RealFuncUtil.getSystemInfo(PerezReverseKillerMain.this);
                runOnUiThread(() -> RealFuncUtil.showDlgMsg(this, getString(R.string.system_info), info, null));
            }).start();
        });
        builder.show();
    }
}
