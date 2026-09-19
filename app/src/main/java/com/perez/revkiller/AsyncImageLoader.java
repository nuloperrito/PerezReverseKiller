package com.perez.revkiller;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.perez.util.RealFuncUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncImageLoader {

    private final LruCache<String, Drawable> imageCache;
    private final Context appContext;
    private final Handler mainHandler;
    private final ExecutorService executorService;

    public AsyncImageLoader(Context ctx) {
        // Prevent Activity context leaks by holding only application context
        this.appContext = (ctx != null) ? ctx.getApplicationContext() : null;

        // Ensure handler binds strictly to the UI thread Looper
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Allocate ~1/8 of available heap memory for the image cache
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        this.imageCache = new LruCache<String, Drawable>(cacheSize > 0 ? cacheSize : 1024) {
            @Override
            protected int sizeOf(String key, Drawable drawable) {
                // Approximate size in KB: width * height * 4 bytes per pixel / 1024
                int width = drawable.getIntrinsicWidth();
                int height = drawable.getIntrinsicHeight();
                if (width > 0 && height > 0) {
                    return (width * height * 4) / 1024;
                }
                return 1;
            }
        };

        // Bounded thread pool: avoid spawning arbitrary number of raw threads
        int corePoolSize = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, 4));
        this.executorService = new ThreadPoolExecutor(
                corePoolSize,
                corePoolSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>()
        );
    }

    public Drawable loadDrawable(final String imageUrl, final ImageView imageView,
                                 final PerezReverseKillerMain.ImageCallback imageCallback) {
        if (imageUrl == null) {
            return getDefaultPlaceholder();
        }

        // Fast memory cache lookup
        Drawable cachedDrawable = imageCache.get(imageUrl);
        if (cachedDrawable != null) {
            return cachedDrawable;
        }

        // Offload loading task to background worker pool
        executorService.execute(() -> {
            final Drawable drawable = RealFuncUtil.showApkIcon(appContext, imageUrl);
            if (drawable != null) {
                imageCache.put(imageUrl, drawable);
            }

            if (imageCallback != null) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        imageCallback.imageLoaded(drawable, imageView);
                    }
                });
            }
        });

        return getDefaultPlaceholder();
    }

    private Drawable getDefaultPlaceholder() {
        if (appContext == null) {
            return null;
        }
        return ContextCompat.getDrawable(appContext, R.drawable.android);
    }
}