package com.perez.revkiller;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.perez.util.WriteThread;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PurgarActivity extends AppCompatActivity implements WriteThread.ThreadControl {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<WriteThread> threads = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String FILE_PATH = Environment.getExternalStorageDirectory()
            + File.separator + "testfile.bin";

    private Button empezar, detener;
    private TextView tvRemSiz, tvWriteSpd;

    private boolean hasStoragePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ?
                Environment.isExternalStorageManager() :
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purgar);

        empezar = findViewById(R.id.empezar);
        detener = findViewById(R.id.detener);
        tvRemSiz = findViewById(R.id.tvRemSiz);
        tvWriteSpd = findViewById(R.id.tvWriteSpd);

        empezar.setOnClickListener(v -> startBackgroundThread());
        detener.setOnClickListener(v -> stopBackgroundThread());

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (hasStoragePermission()) {
                    long freeSpace = getAvailableStorageSpaceInGB();
                    tvRemSiz.setText(String.format(getString(R.string.avail_space_hints), freeSpace));
                    // Only trigger stop if threads are actively running
                    if (freeSpace < 2 && running.get()) {
                        stopBackgroundThread();
                    }
                }
                handler.postDelayed(this, 500);
            }
        }, 0);
    }

    private void startBackgroundThread() {
        if (!hasStoragePermission()) {
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        empezar.setEnabled(false);
        detener.setEnabled(true);

        threads.clear();
        int threadCount = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < threadCount; i++) {
            WriteThread writeThread = new WriteThread(FILE_PATH, this, this);
            threads.add(writeThread);
            writeThread.start();
        }
    }

    private void stopBackgroundThread() {
        // Guard against redundant stops
        if (!running.compareAndSet(true, false)) {
            cleanUpFileOnly();
            return;
        }

        empezar.setEnabled(false);
        detener.setEnabled(false);

        // Perform thread joins and file deletion off the UI thread to prevent ANR
        executor.execute(() -> {
            for (WriteThread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            threads.clear();

            boolean deleteSuccess = deleteFileIfExists(FILE_PATH);

            runOnUiThread(() -> {
                empezar.setEnabled(true);
                detener.setEnabled(false);
                if (!deleteSuccess) {
                    Toast.makeText(PurgarActivity.this, "Cannot delete overlay file!", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void cleanUpFileOnly() {
        executor.execute(() -> {
            File f = new File(FILE_PATH);
            if (f.exists() && !f.delete()) {
                runOnUiThread(() -> Toast.makeText(PurgarActivity.this, "Cannot delete overlay file!", Toast.LENGTH_LONG).show());
            }
        });
    }

    private boolean deleteFileIfExists(String path) {
        File f = new File(path);
        // If file does not exist, consider it already cleaned up successfully
        if (!f.exists()) {
            return true;
        }
        return f.delete();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        stopBackgroundThread();
        executor.shutdown();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean shouldRun() {
        return running.get();
    }

    @Override
    public void updateText(String str) {
        runOnUiThread(() -> tvWriteSpd.setText(str));
    }

    private long getAvailableStorageSpaceInGB() {
        File storageDir = Environment.getExternalStorageDirectory();
        return storageDir.getFreeSpace() / (1024 * 1024 * 1024);
    }
}