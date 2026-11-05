package com.perez.palette;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.perez.revkiller.R;

public class SketchActivity extends AppCompatActivity implements View.OnClickListener, PaletteView.Callback, Handler.Callback {
    private View mUndoView;
    private View mRedoView;
    private View mPenView;
    private View mEraserView;
    private View mClearView;
    private PaletteView mPaletteView;
    private ProgressDialog mSaveProgressDlg;
    private static final int MSG_SAVE_SUCCESS = 1;
    private static final int MSG_SAVE_FAILED = 2;
    private Handler mHandler;
    public static String m_savedPath = "<NULL>";

    private boolean mExitAfterSave = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sketcher);
        setTitle(R.string.palette_app);
        mPaletteView = findViewById(R.id.palette);
        mPaletteView.setCallback(this);
        mUndoView = findViewById(R.id.undo);
        mRedoView = findViewById(R.id.redo);
        mPenView = findViewById(R.id.pen);
        mPenView.setSelected(true);
        mEraserView = findViewById(R.id.eraser);
        mClearView = findViewById(R.id.clear);

        mUndoView.setOnClickListener(this);
        mRedoView.setOnClickListener(this);
        mPenView.setOnClickListener(this);
        mEraserView.setOnClickListener(this);
        mClearView.setOnClickListener(this);

        mUndoView.setEnabled(false);
        mRedoView.setEnabled(false);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
        mHandler = new Handler(this);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleExit();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeMessages(MSG_SAVE_FAILED);
        mHandler.removeMessages(MSG_SAVE_SUCCESS);
    }

    private void initSaveProgressDlg() {
        mSaveProgressDlg = new ProgressDialog(this);
        mSaveProgressDlg.setMessage(getString(R.string.palette_saving));
        mSaveProgressDlg.setCancelable(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.guardar, menu);
        return true;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_SAVE_FAILED:
                mSaveProgressDlg.dismiss();
                Toast.makeText(this, R.string.palette_fs, Toast.LENGTH_LONG).show();
                mExitAfterSave = false;
                break;
            case MSG_SAVE_SUCCESS:
                mSaveProgressDlg.dismiss();
                Toast.makeText(this, getString(R.string.palette_sto) + m_savedPath, Toast.LENGTH_LONG).show();
                mPaletteView.setModified(false);
                if (mExitAfterSave) {
                    finish();
                }
                break;
        }
        return true;
    }

    private static void scanFile(Context context, String filePath) {
        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        scanIntent.setData(Uri.fromFile(new File(filePath)));
        context.sendBroadcast(scanIntent);
        m_savedPath = filePath;
    }

    @Nullable
    public static String saveImage(@Nullable Bitmap bmp, int quality) {
        if (bmp == null || bmp.isRecycled()) {
            return null;
        }
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null) {
            return null;
        }
        if (!picturesDir.exists() && !picturesDir.mkdirs()) {
            Log.e(SketchActivity.class.getName(), "saveImage: Failed to create target directory: " + picturesDir.getAbsolutePath());
            return null;
        }
        String fileName = System.currentTimeMillis() + ".jpg";
        File destinationFile = new File(picturesDir, fileName);
        try (OutputStream fos = new FileOutputStream(destinationFile)) {
            boolean success = bmp.compress(Bitmap.CompressFormat.JPEG, quality, fos);
            if (!success) {
                throw new IOException("Bitmap compression returned false.");
            }
            fos.flush();
            return destinationFile.getAbsolutePath();
        } catch (IOException | SecurityException e) {
            Log.e(SketchActivity.class.getName(), "saveImage: Failed to write bitmap to " + destinationFile.getAbsolutePath(), e);
            if (destinationFile.exists()) destinationFile.delete();
            return null;
        }
    }

    private void executeSave(boolean exitAfterSave) {
        mExitAfterSave = exitAfterSave;
        if (mSaveProgressDlg == null) initSaveProgressDlg();
        mSaveProgressDlg.show();
        new Thread(() -> {
            Bitmap bm = mPaletteView.buildBitmap();
            String savedFile = saveImage(bm, 95);
            if (savedFile != null) {
                scanFile(SketchActivity.this, savedFile);
                mHandler.obtainMessage(MSG_SAVE_SUCCESS).sendToTarget();
            } else {
                mHandler.obtainMessage(MSG_SAVE_FAILED).sendToTarget();
            }
        }).start();
    }

    private void handleExit() {
        if (mPaletteView != null && mPaletteView.isModified()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.prompt)
                    .setMessage(R.string.palette_unsaved_prompt)
                    .setPositiveButton(R.string.save_file, (dialog, which) -> executeSave(true))
                    .setNegativeButton(R.string.discard_unsaved, (dialog, which) -> finish())
                    .setNeutralButton(android.R.string.cancel, null)
                    .show();
        } else {
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save: {
                executeSave(false);
                break;
            }
            case android.R.id.home: {
                handleExit();
                break;
            }
        }
        return true;
    }

    @Override
    public void onUndoRedoStatusChanged() {
        mUndoView.setEnabled(mPaletteView.canUndo());
        mRedoView.setEnabled(mPaletteView.canRedo());
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.undo:
                mPaletteView.undo();
                break;
            case R.id.redo:
                mPaletteView.redo();
                break;
            case R.id.pen:
                v.setSelected(true);
                mEraserView.setSelected(false);
                mPaletteView.setMode(PaletteView.Mode.DRAW);
                break;
            case R.id.eraser:
                v.setSelected(true);
                mPenView.setSelected(false);
                mPaletteView.setMode(PaletteView.Mode.ERASER);
                break;
            case R.id.clear:
                mPaletteView.clear();
                break;
        }
    }
}
