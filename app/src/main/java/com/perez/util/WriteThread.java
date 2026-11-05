package com.perez.util;

import android.content.Context;

import com.perez.revkiller.R;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class WriteThread extends Thread {

    public interface ThreadControl {
        boolean shouldRun();
        void updateText(String str);
    }

    private final String filePath;
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB buffer size
    private final ThreadControl control;
    private final Context mCtx;
    private static final AtomicLong totalBytesWritten = new AtomicLong(0);
    private static final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

    public WriteThread(String filePath, ThreadControl control, Context ctx) {
        this.filePath = filePath;
        this.control = control;
        this.mCtx = ctx;
    }

    @Override
    public void run() {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath, true))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            Random random = new Random();

            while (control.shouldRun()) {
                random.nextBytes(buffer);
                bos.write(buffer);
                bos.flush();
                long bytesWritten = totalBytesWritten.addAndGet(BUFFER_SIZE);
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - startTime.get();

                if (elapsedTime > 1000) {
                    double speed = (bytesWritten / 1024.0 / 1024.0) / (elapsedTime / 1000.0);
                    control.updateText(String.format(mCtx.getString(R.string.cur_write_speed) + "\n", speed));
                    startTime.set(currentTime);
                    totalBytesWritten.set(0);
                }
            }
        } catch (IOException e) {
            // Stream closed or disk full
            e.printStackTrace();
        }
    }
}
