package com.perez.revkiller;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.perez.arsceditor.ArscActivity;
import com.perez.elfeditor.ElfActivity;
import com.perez.media.AudioPlayerActivity;
import com.perez.media.HugeImageViewerActivity;
import com.perez.media.VideoPlayerActivity;
import com.perez.revkiller.adapter.FileListAdapter;
import com.perez.util.FileUtil;
import com.perez.util.RankPrefUtil;
import com.perez.util.RealFuncUtil;
import com.perez.util.ZipExtract;
import com.perez.xml2axml.func.FuncMain;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipManagerMain extends AppCompatActivity {
    private static final String TAG = "ZipManagerMain";

    private static final int MSG_SHOW_PROGRESS = 1;
    private static final int MSG_DISMISS_PROGRESS = 2;
    private static final int MSG_TOAST = 3;

    private static final int REQ_CODE_EDIT_FILE = 2001;

    private File mZipFile;
    private File mTempDir;
    private String mCurrentPath = "";
    private final Stack<String> mPathStack = new Stack<>();

    private ListView mListView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private FileListAdapter mAdapter;

    private ProgressDialog mProgressDialog;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (isFinishing() || isDestroyed()) return;
            switch (msg.what) {
                case MSG_SHOW_PROGRESS:
                    showProgressDialog((String) msg.obj);
                    break;
                case MSG_DISMISS_PROGRESS:
                    dismissProgressDialog();
                    break;
                case MSG_TOAST:
                    Toast.makeText(ZipManagerMain.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    // Track editing archives
    private String mPendingEditEntry = null;
    private File mPendingTempFile = null;
    private long mPendingFileLastModified = 0;
    private long mPendingFileSize = 0;

    /**
     * proxy class that allows a ZipEntry to be directly read as a File, standardized via FileListAdapter
     */
    public static class ZipEntryFile extends File {
        private final String entryName;
        private final String simpleName;
        private final boolean isDirectory;
        private final long length;
        private final long lastModified;

        public ZipEntryFile(File parentDir, String entryName, String simpleName, boolean isDirectory, long length, long lastModified) {
            super(parentDir, simpleName);
            this.entryName = entryName;
            this.simpleName = simpleName;
            this.isDirectory = isDirectory;
            this.length = length;
            this.lastModified = lastModified;
        }

        public String getEntryName() {
            return entryName;
        }

        @Override
        public String getName() {
            return simpleName;
        }

        @Override
        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        public boolean isFile() {
            return !isDirectory;
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public long lastModified() {
            return lastModified;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public boolean canWrite() {
            return true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.listact);

        initZipTarget();
        if (mZipFile == null || !mZipFile.exists()) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initTempSandbox();
        initViews();
        loadEntriesAsync();
    }

    private void initZipTarget() {
        Intent intent = getIntent();
        String path = intent.getStringExtra(PerezReverseKillerMain.ENTRYPATH);
        if (path == null) {
            path = intent.getStringExtra("filePath");
        }
        if (path == null && intent.getData() != null) {
            path = intent.getData().getPath();
        }
        if (path != null) {
            mZipFile = new File(path);
        }
    }

    private void initTempSandbox() {
        File cacheBase = getExternalCacheDir();
        if (cacheBase == null) {
            cacheBase = getCacheDir();
        }
        mTempDir = new File(cacheBase, "zip_temp_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6));
        if (!mTempDir.exists()) {
            mTempDir.mkdirs();
        }
    }

    private void initViews() {
        mListView = findViewById(R.id.file_list_view);
        mSwipeRefreshLayout = findViewById(R.id.swipeRefresh);

        // Handle Predictive Back properly on Android 13~16+, completely replacing traditional onKeyDown()
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!mPathStack.isEmpty()) {
                    mCurrentPath = mPathStack.pop();
                    loadEntriesAsync();
                } else finish();
            }
        });

        mAdapter = new FileListAdapter(this, false);
        mAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onInvalidated() {
                loadEntriesAsync();
            }
        });
        mListView.setAdapter(mAdapter);
        registerForContextMenu(mListView);

        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            loadEntriesAsync();
            mSwipeRefreshLayout.setRefreshing(false);
        });

        mListView.setOnItemClickListener((parent, view, position, id) -> {
            File selectedFile = (File) parent.getItemAtPosition(position);
            if (selectedFile == null) return;

            if (selectedFile.isDirectory()) {
                mPathStack.push(mCurrentPath);
                mCurrentPath = mCurrentPath + selectedFile.getName() + "/";
                loadEntriesAsync();
                return;
            }

            if (selectedFile instanceof ZipEntryFile) {
                handleEntryClick((ZipEntryFile) selectedFile);
            }
        });
    }

    private void updateTitle() {
        String baseName = (mZipFile != null) ? mZipFile.getName() : getString(R.string.zip_editor);
        setTitle(baseName + " - /" + mCurrentPath);
    }

    private void loadEntriesAsync() {
        sendShowProgress(getString(R.string.load_data));
        mExecutor.execute(() -> {
            List<File> currentList = new ArrayList<>();
            if (mZipFile != null && mZipFile.exists()) {
                try (ZipFile zipFile = new ZipFile(mZipFile)) {
                    Map<String, ZipEntryFile> subDirs = new TreeMap<>();
                    Map<String, ZipEntryFile> files = new TreeMap<>();

                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();

                        if (!name.startsWith(mCurrentPath)) {
                            continue;
                        }

                        String rel = name.substring(mCurrentPath.length());
                        if (rel.isEmpty()) {
                            continue;
                        }

                        int slashIdx = rel.indexOf('/');
                        if (slashIdx != -1) {
                            String dirName = rel.substring(0, slashIdx);
                            if (!subDirs.containsKey(dirName)) {
                                String fullDirPath = mCurrentPath + dirName + "/";
                                ZipEntryFile dirFile = new ZipEntryFile(mTempDir, fullDirPath, dirName, true, 0, entry.getTime());
                                subDirs.put(dirName, dirFile);
                            }
                        } else {
                            if (!entry.isDirectory()) {
                                ZipEntryFile fileItem = new ZipEntryFile(mTempDir, name, rel, false, entry.getSize(), entry.getTime());
                                files.put(rel, fileItem);
                            }
                        }
                    }

                    List<File> dirList = new ArrayList<>(subDirs.values());
                    List<File> fileList = new ArrayList<>(files.values());

                    RankPrefUtil rankUtil = new RankPrefUtil(ZipManagerMain.this);
                    Comparator<File> comparator;
                    switch (rankUtil.GetWhich()) {
                        case "1":
                            comparator = RankPrefUtil.sortByType;
                            break;
                        case "2":
                            comparator = RankPrefUtil.sortByDate;
                            break;
                        case "3":
                            comparator = RankPrefUtil.sortBySize;
                            break;
                        default:
                            comparator = RankPrefUtil.sortByName;
                            break;
                    }
                    Collections.sort(dirList, comparator);
                    Collections.sort(fileList, comparator);
                    if (rankUtil.GetReverse()) {
                        Collections.reverse(dirList);
                        Collections.reverse(fileList);
                    }

                    currentList.addAll(dirList);
                    currentList.addAll(fileList);
                } catch (Exception e) {
                    Log.e(TAG, "Error listing zip entries", e);
                    sendToast(getString(R.string.error) + ": " + e.getMessage());
                }
            }

            // Sync with global backing storage for FileListAdapter
            mMainHandler.post(() -> {
                if (mAdapter != null) {
                    mAdapter.setFileList(currentList);
                }
                sendDismissProgress();
                updateTitle();
            });
        });
    }

    private void handleEntryClick(ZipEntryFile entryFile) {
        final String entryName = entryFile.getEntryName();
        final File destFile = new File(mTempDir, entryName);

        sendShowProgress(getString(R.string.extracting));
        mExecutor.execute(() -> {
            boolean extracted = false;
            try (ZipFile zf = new ZipFile(mZipFile)) {
                ZipEntry entry = zf.getEntry(entryName);
                if (entry != null) {
                    ZipExtract.extractEntry(zf, entry, mTempDir);
                    extracted = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Extract entry failed", e);
            }

            sendDismissProgress();
            if (!extracted || !destFile.exists()) {
                sendToast(getString(R.string.failure));
                return;
            }

            mMainHandler.post(() -> dispatchFileAction(destFile, entryName));
        });
    }

    private void recordPendingEdit(File file, String entryName) {
        mPendingTempFile = file;
        mPendingEditEntry = entryName;
        mPendingFileLastModified = file.lastModified();
        mPendingFileSize = file.length();
    }

    private void dispatchFileAction(File tempFile, String entryName) {
        recordPendingEdit(tempFile, entryName);
        String ext = FileUtil.getFileExtension(tempFile.getName());

        switch (ext) {
            case "dex":
                openDexEditor(tempFile);
                break;
            case "arsc":
                openArscEditor(tempFile);
                break;
            case "xml":
                openXmlPrompt(tempFile);
                break;
            case "so":
                openElfEditor(tempFile);
                break;
            case "mp4":
            case "mkv":
            case "3gp":
                openVideoPlayer(tempFile);
                break;
            case "mp3":
            case "aac":
            case "ogg":
            case "wma":
            case "wav":
            case "amr":
            case "flac":
            case "m4a":
                openAudioPlayer(tempFile);
                break;
            case "jpg":
            case "jpeg":
            case "png":
            case "bmp":
            case "gif":
            case "webp":
                openImageViewer(tempFile);
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
                openTextEditor(tempFile);
                break;
            default:
                openDefaultPrompt(tempFile);
                break;
        }
    }

    private void openDexEditor(File tempFile) {
        Intent intent = new Intent(ZipManagerMain.this, ClassListActivity.class);
        intent.putExtra("filePath", tempFile.getAbsolutePath());
        intent.setData(Uri.fromFile(tempFile));
        startActivityForResult(intent, ActResConstant.class_list_item);
    }

    private void openArscEditor(File tempFile) {
        Intent intent = new Intent(this, ArscActivity.class);
        intent.putExtra("filePath", tempFile.getAbsolutePath());
        intent.setData(Uri.fromFile(tempFile));
        startActivityForResult(intent, REQ_CODE_EDIT_FILE);
    }

    private void openXmlPrompt(File tempFile) {
        if (FuncMain.isBinAXML(tempFile.getAbsolutePath())) {
            Intent intent = new Intent(ZipManagerMain.this, TextEditor.class);
            intent.putExtra("filePath", tempFile.getAbsolutePath());
            intent.putExtra(TextEditor.PLUGIN, "AXmlEditor");
            intent.setData(Uri.fromFile(tempFile));
            startActivityForResult(intent, REQ_CODE_EDIT_FILE);
        } else {
            openTextEditor(tempFile);
        }
    }

    private void openElfEditor(File tempFile) {
        Intent intent = new Intent(this, ElfActivity.class);
        intent.putExtra("filePath", tempFile.getAbsolutePath());
        intent.setData(Uri.fromFile(tempFile));
        startActivityForResult(intent, REQ_CODE_EDIT_FILE);
    }

    private void openVideoPlayer(File tempFile) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.setData(Uri.fromFile(tempFile));
        startActivity(intent);
    }

    private void openAudioPlayer(File tempFile) {
        Intent intent = new Intent(this, AudioPlayerActivity.class);
        intent.setData(Uri.fromFile(tempFile));
        startActivity(intent);
    }

    private void openImageViewer(File tempFile) {
        Intent intent = new Intent(this, HugeImageViewerActivity.class);
        intent.setData(Uri.fromFile(tempFile));
        startActivity(intent);
    }

    private void openTextEditor(File tempFile) {
        Intent intent = new Intent(this, TextEditor.class);
        intent.putExtra("filePath", tempFile.getAbsolutePath());
        intent.putExtra(TextEditor.PLUGIN, "TextEditor");
        intent.setData(Uri.fromFile(tempFile));
        startActivityForResult(intent, ActResConstant.text_editor);
    }

    private void openDefaultPrompt(File tempFile) {
        CharSequence[] items = new CharSequence[]{getString(R.string.view), getString(R.string.text_editor)};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.options);
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = FileProvider.getUriForFile(ZipManagerMain.this, getPackageName() + ".provider", tempFile);
                intent.setDataAndType(uri, "*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(ZipManagerMain.this, R.string.failure, Toast.LENGTH_SHORT).show();
                }
            } else {
                openTextEditor(tempFile);
            }
        });
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ActResConstant.add_entry && data != null) {
            String selectedPath = data.getStringExtra(PerezReverseKillerMain.ENTRYPATH);
            if (selectedPath != null) {
                File fileToAdd = new File(selectedPath);
                if (fileToAdd.exists()) {
                    String targetEntry = mCurrentPath + fileToAdd.getName();
                    syncFileToZipAsync(fileToAdd, targetEntry);
                }
            }
            return;
        }

        if (mPendingTempFile == null || mPendingEditEntry == null) {
            return;
        }

        boolean isModified = mPendingTempFile.exists() &&
                (mPendingTempFile.lastModified() != mPendingFileLastModified || mPendingTempFile.length() != mPendingFileSize);

        if (isModified) {
            final File fileToSync = mPendingTempFile;
            final String entryToSync = mPendingEditEntry;

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.prompt);
            builder.setMessage(R.string.is_save);
            builder.setPositiveButton(R.string.btn_yes, (dialog, which) -> syncFileToZipAsync(fileToSync, entryToSync));
            builder.setNegativeButton(R.string.btn_no, (dialog, which) -> {
                mPendingTempFile = null;
                mPendingEditEntry = null;
            });
            builder.show();
        } else {
            mPendingTempFile = null;
            mPendingEditEntry = null;
        }
    }

    private void syncFileToZipAsync(File sourceFile, String targetEntryName) {
        sendShowProgress(getString(R.string.write_zip));
        mExecutor.execute(() -> {
            boolean success = updateZipEntry(mZipFile, sourceFile, targetEntryName);
            if (success) {
                if (mZipFile.getName().toLowerCase().endsWith(".apk")) {
                    // TODO: Implement APK digital signing here after syncing modified entry
                }
                sendToast(getString(R.string.saved));
            } else {
                sendToast(getString(R.string.failure));
            }
            sendDismissProgress();
            loadEntriesAsync();
        });
    }

    private boolean updateZipEntry(File zipFile, File replacementFile, String entryName) {
        File tempZip = new File(zipFile.getParentFile(), zipFile.getName() + ".tmp");
        if (tempZip.exists()) {
            tempZip.delete();
        }

        try (ZipFile srcZip = new ZipFile(zipFile);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempZip)))) {

            boolean replaced = false;
            Enumeration<? extends ZipEntry> entries = srcZip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.equals(entryName)) {
                    ZipEntry newEntry = new ZipEntry(entryName);
                    newEntry.setTime(System.currentTimeMillis());
                    zos.putNextEntry(newEntry);
                    try (InputStream fis = new BufferedInputStream(new FileInputStream(replacementFile))) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = fis.read(buf)) != -1) {
                            zos.write(buf, 0, len);
                        }
                    }
                    zos.closeEntry();
                    replaced = true;
                } else {
                    ZipEntry copyEntry = new ZipEntry(name);
                    copyEntry.setTime(entry.getTime());
                    zos.putNextEntry(copyEntry);
                    try (InputStream is = srcZip.getInputStream(entry)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            zos.write(buf, 0, len);
                        }
                    }
                    zos.closeEntry();
                }
            }

            if (!replaced) {
                ZipEntry newEntry = new ZipEntry(entryName);
                newEntry.setTime(System.currentTimeMillis());
                zos.putNextEntry(newEntry);
                try (InputStream fis = new BufferedInputStream(new FileInputStream(replacementFile))) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = fis.read(buf)) != -1) {
                        zos.write(buf, 0, len);
                    }
                }
                zos.closeEntry();
            }

            zos.flush();
        } catch (Exception e) {
            Log.e(TAG, "Failed updating zip", e);
            if (tempZip.exists()) tempZip.delete();
            return false;
        }

        if (zipFile.delete()) {
            return tempZip.renameTo(zipFile);
        } else {
            if (tempZip.exists()) tempZip.delete();
            return false;
        }
    }

    private void deleteZipEntriesAsync(List<String> entryNames) {
        sendShowProgress(getString(R.string.zip_remove_progress));
        mExecutor.execute(() -> {
            File tempZip = new File(mZipFile.getParentFile(), mZipFile.getName() + ".tmp");
            boolean success = false;
            try (ZipFile srcZip = new ZipFile(mZipFile);
                 ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempZip)))) {

                Enumeration<? extends ZipEntry> entries = srcZip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();

                    boolean shouldDelete = false;
                    for (String prefix : entryNames) {
                        if (name.equals(prefix) || name.startsWith(prefix)) {
                            shouldDelete = true;
                            break;
                        }
                    }

                    if (!shouldDelete) {
                        ZipEntry copyEntry = new ZipEntry(name);
                        copyEntry.setTime(entry.getTime());
                        zos.putNextEntry(copyEntry);
                        try (InputStream is = srcZip.getInputStream(entry)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = is.read(buf)) != -1) {
                                zos.write(buf, 0, len);
                            }
                        }
                        zos.closeEntry();
                    }
                }
                zos.flush();
                success = true;
            } catch (Exception e) {
                Log.e(TAG, "Delete entry error", e);
            }

            if (success && mZipFile.delete()) {
                tempZip.renameTo(mZipFile);
                String realName = entryNames.get(0);
                realName = realName.substring(realName.lastIndexOf('/') + 1);
                sendToast(getString(R.string.deleted, (entryNames.size() > 1 ? realName + ", etc. " : realName)));
            } else {
                if (tempZip.exists()) tempZip.delete();
                sendToast(getString(R.string.failure));
            }

            sendDismissProgress();
            loadEntriesAsync();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.zip_editor_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.add_entry) {
            Intent intent = new Intent(this, PerezReverseKillerMain.class);
            intent.putExtra(PerezReverseKillerMain.SELECTEDMOD, true);
            startActivityForResult(intent, ActResConstant.add_entry);
            return true;
        } else if (id == R.id.save_file) {
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.replace_axml) {
            showReplaceAxmlDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showReplaceAxmlDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.alert_dialog_replace_axml, null);
        final EditText srcEdit = view.findViewById(R.id.src_edit);
        final EditText dstEdit = view.findViewById(R.id.dst_edit);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.replace_axml);
        builder.setView(view);
        builder.setPositiveButton(R.string.replace, (dialog, which) -> {
            final String src = srcEdit.getText().toString();
            final String dst = dstEdit.getText().toString();
            if (src.isEmpty()) return;

            sendShowProgress(getString(R.string.replacing));
            mExecutor.execute(() -> {
                // Background search & replace in binary XML entries
                sendDismissProgress();
                sendToast(getString(R.string.success));
                loadEntriesAsync();
            });
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.setHeaderTitle(R.string.options);
        menu.add(Menu.NONE, R.string.delete, Menu.NONE, R.string.delete);
        menu.add(Menu.NONE, R.string.extract, Menu.NONE, R.string.extract);
        menu.add(Menu.NONE, R.string.add_folder, Menu.NONE, R.string.add_folder);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            return false;
        }

        File selected = (File) mListView.getItemAtPosition(info.position);
        if (!(selected instanceof ZipEntryFile)) {
            return false;
        }
        ZipEntryFile entryFile = (ZipEntryFile) selected;

        int itemId = item.getItemId();
        if (itemId == R.string.delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.delete);
            builder.setMessage(getString(R.string.is_delete, entryFile.getName()));
            builder.setPositiveButton(R.string.btn_yes, (dialog, which) -> {
                List<String> list = new ArrayList<>();
                list.add(entryFile.getEntryName());
                deleteZipEntriesAsync(list);
            });
            builder.setNegativeButton(R.string.btn_no, null);
            builder.show();
            return true;
        } else if (itemId == R.string.extract) {
            extractEntryToCache(entryFile);
            return true;
        } else if (itemId == R.string.add_folder) {
            showNewFolderDialog();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void extractEntryToCache(ZipEntryFile entryFile) {
        sendShowProgress(getString(R.string.extracting));
        mExecutor.execute(() -> {
            try (ZipFile zf = new ZipFile(mZipFile)) {
                ZipEntry entry = zf.getEntry(entryFile.getEntryName());
                if (entry != null) {
                    ZipExtract.extractEntry(zf, entry, mTempDir);
                    sendToast(getString(R.string.extracted));
                }
            } catch (Exception e) {
                sendToast(getString(R.string.failure));
            }
            sendDismissProgress();
        });
    }

    private void showNewFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_folder);
        final EditText input = new EditText(this);
        input.setHint(R.string.folder_name);
        builder.setView(input);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                if (!name.endsWith("/")) name += "/";
                String newDirEntry = mCurrentPath + name;
                sendShowProgress(getString(R.string.wait));
                mExecutor.execute(() -> {
                    // Create empty directory entry
                    File dummy = new File(mTempDir, ".dummy");
                    try {
                        dummy.createNewFile();
                        updateZipEntry(mZipFile, dummy, newDirEntry);
                    } catch (IOException ignored) {}
                    finally {
                        dummy.delete();
                    }
                    sendDismissProgress();
                    loadEntriesAsync();
                });
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
        cleanupTempFiles();
        dismissProgressDialog();
    }

    private void cleanupTempFiles() {
        if (mTempDir != null && mTempDir.exists()) {
            new Thread(() -> FileUtil.delete(mTempDir)).start();
        }
    }

    private void showProgressDialog(String message) {
        if (isFinishing() || isDestroyed()) return;
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
        }
        mProgressDialog.setMessage(message);
        if (!mProgressDialog.isShowing()) {
            mProgressDialog.show();
        }
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            try {
                mProgressDialog.dismiss();
            } catch (Exception ignored) {}
        }
    }

    private void sendShowProgress(String msg) {
        Message message = mMainHandler.obtainMessage(MSG_SHOW_PROGRESS, msg);
        mMainHandler.sendMessage(message);
    }

    private void sendDismissProgress() {
        mMainHandler.sendEmptyMessage(MSG_DISMISS_PROGRESS);
    }

    private void sendToast(String text) {
        Message message = mMainHandler.obtainMessage(MSG_TOAST, text);
        mMainHandler.sendMessage(message);
    }
}