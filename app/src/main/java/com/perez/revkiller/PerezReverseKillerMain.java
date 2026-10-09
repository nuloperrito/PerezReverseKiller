package com.perez.revkiller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import com.perez.exifremover.Interfaz;
import com.perez.revkiller.adapter.FileListAdapter;
import com.perez.util.FileUtil;
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
import java.net.URLConnection;
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
    private ProgressDialog mFileListLoadingDialog;
    private final ExecutorService mFileScanExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger mCurrentScanToken = new AtomicInteger(0);

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
            loadFileListAsync("", -1);
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
        fileList.setAdapter(mAdapter);

        // Initial loading should be performed asynchronously, then restore the position after loading is complete
        loadFileListAsync("", position);

        if(mPermissionDialog == null) {
            mPermissionDialog = new Dialog(this);
            mPermissionDialog.setContentView(R.layout.permissions);
            mPermissionDialog.findViewById(R.id.btnOk).setOnClickListener(v -> setPermissions());
            mPermissionDialog.findViewById(R.id.btnCancel).setOnClickListener(v -> mPermissionDialog.hide());
        }

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
                    // Prevent entering directories that cannot be read without root
                    if (!FileUtil.canListFiles(file)) return;
                    mCurrentDir = file;
                    pos.push(parent.getFirstVisiblePosition());
                    loadFileListAsync("", 0);
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
        String ext_name = FileUtil.getFileExtension(file.getName());
        menu.add(Menu.NONE, R.string.delete, Menu.NONE, R.string.delete);
        menu.add(Menu.NONE, R.string.rename, Menu.NONE, R.string.rename);
        if(RealFuncUtil.isZip(file)) {
            menu.add(Menu.NONE, R.string.sign_apk, Menu.NONE, R.string.sign_apk);
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
                case R.string.sign_apk:
                    digitalSignApk(mCurrent);
                    return true;
                case R.string.rename:
                    rename(mCurrent);
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
                    new Thread(() -> {
                        if(FileUtil.convertToPng(mCurrent.getPath(), FileUtil.getFileNameNoEx(mCurrent.getPath()) + ".png"))
                            showToast(false, getString(R.string.img_conv_suc));
                        else showToast(false, getString(R.string.img_conv_failed));
                        runOnUiThread(() -> mAdapter.notifyDataSetInvalidated());
                    }).start();
                    return true;
                case R.string.str_png2jpg:
                    new Thread(() -> {
                        String sourcePath = mCurrent.getPath();
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
                    }).start();
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
        if (mSelectMod) {
            // In file picking mode, exit the activity on back press
            finish();
        }

        // Normal browsing mode: intercept back press to stay at current accessible root
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

        // Handle Predictive Back properly on Android 13~16+, completely replacing traditional onKeyDown()
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressAction();
            }
        });

        String[] pmList1 = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE};
        String[] pmList2 = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (hasPermission(this, pmList1) || !Environment.isExternalStorageManager())) {
            ActivityCompat.requestPermissions(PerezReverseKillerMain.this, pmList1, RQ_PERMISSION);
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + this.getPackageName()));
            startActivityForResult(intent, RQ_PERMISSION);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && hasPermission(this, pmList2)) {
            ActivityCompat.requestPermissions(PerezReverseKillerMain.this, pmList2, RQ_PERMISSION);
        } else {
            CreateInit();
        }
    }

    private void showFileListLoadingDialog() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }
        if (mFileListLoadingDialog == null) {
            mFileListLoadingDialog = new ProgressDialog(this);
            mFileListLoadingDialog.setMessage(getString(R.string.loading_please_wait));
            mFileListLoadingDialog.setIndeterminate(true);
            mFileListLoadingDialog.setCancelable(false);
        }
        if (!mFileListLoadingDialog.isShowing()) {
            mFileListLoadingDialog.show();
        }
    }

    private void dismissFileListLoadingDialog() {
        if (mFileListLoadingDialog != null && mFileListLoadingDialog.isShowing()) {
            try {
                mFileListLoadingDialog.dismiss();
            } catch (Exception ignored) {}
        }
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

        showFileListLoadingDialog();

        // Delegate directory scanning, filtering, and sorting to background threads
        mFileScanExecutor.execute(() -> {
            File[] files = scanDir.listFiles();

            // If directory is inaccessible (permission denied), abort and keep current list intact
            if (files == null) {
                runOnUiThread(() -> {
                    if (token != mCurrentScanToken.get()) {
                        return;
                    }
                    dismissFileListLoadingDialog();
                    if (srLayout != null && srLayout.isRefreshing()) {
                        srLayout.setRefreshing(false);
                    }
                });
                return;
            }

            final List<File> work = new ArrayList<>();
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PerezReverseKillerMain.this);
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
            if (parent != null && FileUtil.canListFiles(parent)) {
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
                dismissFileListLoadingDialog();
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
        switch(requestCode) {
            case ActResConstant.list_item_details:
                switch(resultCode) {
                    case ActResConstant.text_editor:
                        if(mCurrent != null) {
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

    public void zipAlign(final File file) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            if(Features.isZipAligned(file.toString())) {
                showToast(false, getString(R.string.zip_has_aligned));
                mHandler.sendEmptyMessage(DISMISSPROGRESS);
                return;
            }
            boolean b = Features.ZipAlign(file.toString(), FileUtil.addSuffixToExtension(file.toString(), "aligned"));
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
            int currentPos = (fileList != null) ? fileList.getFirstVisiblePosition() : -1;
            loadFileListAsync("", currentPos);
            initRealtimeCallForwardingListener();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();
        menu.add(Menu.NONE, R.string.add_folder, Menu.NONE, R.string.add_folder);
        menu.add(Menu.NONE, R.string.pref_title_settings, Menu.NONE, R.string.pref_title_settings);
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
        dismissFileListLoadingDialog();
        unregisterRealtimeCallForwardingListener();
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
            case R.string.paste:
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
            case R.string.call_forwarding: {
                toggleCallF();
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
        }
        return true;
    }

    private void digitalSignApk(final File file) {
        new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            try {
                String out = file.getAbsolutePath();
                out = FileUtil.addSuffixToExtension(out, "signed");
                // TODO: Implement APK signing here
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

    private void delete(final File file) {
        RealFuncUtil.showDlgMsg(this, getString(R.string.delete), String.format(getString(R.string.is_delete), file.getName()),
                (dlg, which) -> {
                    if(which == AlertDialog.BUTTON_POSITIVE) {
                        new Thread(() -> {
                            mHandler.sendEmptyMessage(SHOWPROGRESS);
                            FileUtil.delete(file);
                            mFileList.remove(file);
                            Message msg = new Message();
                            msg.what = TOAST;
                            msg.obj = String.format(getString(R.string.deleted), file.getName());
                            mHandler.sendMessage(msg);
                            mHandler.sendEmptyMessage(DISMISSPROGRESS);
                        }).start();
                    }
                });
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

    private void procXml(final File file) {
        String message;
        Runnable positiveAction, neutralAction;

        if (FuncMain.isBinAXML(file.toString())) {
            message = getString(R.string.axml_instruction);
            positiveAction = () -> {
                boolean success = false;
                mHandler.sendEmptyMessage(SHOWPROGRESS);
                try {
                    FuncMain.decode(file.toString(), FileUtil.addSuffixToExtension(file.toString(), "_dec"));
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
                            FileUtil.addSuffixToExtension(file.toString(), "_comp"));
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
        builder.setTitle(getString(R.string.tips));
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
        builder.setTitle(getString(R.string.tips));
        builder.setMessage(getString(R.string.dex_instruction));
        builder.setPositiveButton(getString(R.string.tojar), (dialog, whichButton) -> new Thread(() -> {
            mHandler.sendEmptyMessage(SHOWPROGRESS);
            try {
                String dest_file = FileUtil.getFileNameNoEx(file.toString()) + "_dex2jar.jar";
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
                RealFuncUtil.showDlgMsg(this, getString(R.string.system_info), info.toString(), null);
            });
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
        } catch (IOException e) {
            e.printStackTrace();
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
}
