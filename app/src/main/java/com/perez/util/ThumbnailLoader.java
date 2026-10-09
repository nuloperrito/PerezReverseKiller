package com.perez.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.widget.ImageView;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Supports common images (jpg, jpeg, png, gif, bmp, webp) and videos (mp4, mkv, flv, 3gp, avi, mov, wmv, rmvb etc.).
 * Supports transparent background display for images, extracting the first frame of GIF, decoding at lower resolution, and using LruCache for smooth performance.
 */
public class ThumbnailLoader {

    private static final int THUMB_TARGET_SIZE = 128; // Target thumbnail max side (px), prevents OOM
    private static volatile ThumbnailLoader sInstance;

    private final Context appContext;
    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final SharedPreferences preferences;

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "bmp", "gif", "webp"
    ));

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "mkv", "flv", "3gp", "avi", "mov", "wmv", "rmvb", "webm", "ts"
    ));

    public static ThumbnailLoader getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ThumbnailLoader.class) {
                if (sInstance == null) {
                    sInstance = new ThumbnailLoader(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private ThumbnailLoader(Context context) {
        this.appContext = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.preferences = PreferenceManager.getDefaultSharedPreferences(appContext);

        // Allocate 1/8 of available heap memory for thumbnail cache
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = Math.max(1024, maxMemory / 8);
        this.memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        // Create a bounded background thread pool to ensure lower priority than the UI thread
        int corePoolSize = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), 3));
        this.executorService = new ThreadPoolExecutor(
                corePoolSize,
                corePoolSize,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private final AtomicInteger count = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "ThumbnailLoader-Worker-" + count.getAndIncrement());
                        t.setPriority(Thread.NORM_PRIORITY - 1);
                        return t;
                    }
                }
        );
    }

    public static boolean isImageFile(File file) {
        if (file == null || file.isDirectory()) return false;
        String ext = FileUtil.getFileExtension(file.getName()).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(ext);
    }

    public static boolean isVideoFile(File file) {
        if (file == null || file.isDirectory()) return false;
        String ext = FileUtil.getFileExtension(file.getName()).toLowerCase(Locale.ROOT);
        return VIDEO_EXTENSIONS.contains(ext);
    }

    public static boolean isSupported(File file) {
        return isImageFile(file) || isVideoFile(file);
    }

    /**
     * Asynchronously loads the thumbnail
     * @param file The file object
     * @param imageView The target View
     * @param defaultResId Default/placeholder resource ID
     */
    public void loadThumbnail(final File file, final ImageView imageView, final int defaultResId) {
        if (file == null || imageView == null) {
            return;
        }

        // Read thumbnail enable switch
        boolean thumbnailsEnabled = preferences.getBoolean("pref_key_enable_thumbnails", true);
        if (!thumbnailsEnabled || !isSupported(file)) {
            imageView.setImageResource(defaultResId);
            imageView.setBackgroundColor(Color.TRANSPARENT);
            return;
        }

        final String path = file.getAbsolutePath();

        // Memory cache hit, render directly on UI thread
        Bitmap cached = memoryCache.get(path);
        if (cached != null && !cached.isRecycled()) {
            imageView.setTag(path);
            imageView.setImageBitmap(cached);
            imageView.setBackgroundColor(Color.TRANSPARENT);
            return;
        }

        // If miss, set default placeholder and tag
        imageView.setTag(path);
        imageView.setImageResource(defaultResId);
        imageView.setBackgroundColor(Color.TRANSPARENT);

        // Submit asynchronous task
        executorService.execute(() -> {
            Bitmap thumbnail = null;
            try {
                if (isImageFile(file)) {
                    thumbnail = decodeSampledBitmapFromFile(path, THUMB_TARGET_SIZE, THUMB_TARGET_SIZE);
                } else if (isVideoFile(file)) {
                    thumbnail = extractVideoThumbnail(path, THUMB_TARGET_SIZE, THUMB_TARGET_SIZE);
                }
            } catch (Throwable ignored) {
            }

            if (thumbnail != null) {
                memoryCache.put(path, thumbnail);
                final Bitmap finalBitmap = thumbnail;
                mainHandler.post(() -> {
                    // Compare Tag to prevent layout recycling errors in ListView / RecyclerView scrolling views
                    Object currentTag = imageView.getTag();
                    if (currentTag instanceof String && path.equals(currentTag)) {
                        imageView.setImageBitmap(finalBitmap);
                        imageView.setBackgroundColor(Color.TRANSPARENT);
                    }
                });
            }
        });
    }

    /**
     * Performs scaled decoding for images, preserving Alpha transparency layer.
     */
    private Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        // Use ARGB_8888 to fully preserve PNG, GIF, WebP transparency layers
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap decoded = BitmapFactory.decodeFile(path, options);
        if (decoded == null) {
            return null;
        }

        // If the decoded size is still much larger than target dimensions, perform aspect-ratio scaled down scaling
        if (decoded.getWidth() > reqWidth * 1.5f || decoded.getHeight() > reqHeight * 1.5f) {
            Bitmap scaled = createScaledBitmapKeepAspect(decoded, reqWidth, reqHeight);
            if (scaled != decoded) {
                decoded.recycle();
            }
            return scaled;
        }
        return decoded;
    }

    /**
     * Extracts a small thumbnail from the video's first frame.
     */
    private Bitmap extractVideoThumbnail(String path, int reqWidth, int reqHeight) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            Bitmap frame = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                frame = retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, reqWidth, reqHeight);
            }
            if (frame == null) {
                frame = retriever.getFrameAtTime();
                if (frame != null) {
                    Bitmap scaled = createScaledBitmapKeepAspect(frame, reqWidth, reqHeight);
                    if (scaled != frame) {
                        frame.recycle();
                    }
                    frame = scaled;
                }
            }
            return frame;
        } catch (Throwable e) {
            // Fall back to ThumbnailUtils
            Bitmap thumb = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MICRO_KIND);
            if (thumb != null) {
                Bitmap scaled = createScaledBitmapKeepAspect(thumb, reqWidth, reqHeight);
                if (scaled != thumb) {
                    thumb.recycle();
                }
                return scaled;
            }
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private Bitmap createScaledBitmapKeepAspect(Bitmap src, int maxW, int maxH) {
        int width = src.getWidth();
        int height = src.getHeight();
        if (width <= 0 || height <= 0) return src;

        float scale = Math.min((float) maxW / width, (float) maxH / height);
        if (scale >= 1.0f) return src;

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        return Bitmap.createBitmap(src, 0, 0, width, height, matrix, true);
    }

    public void clearCache() {
        if (memoryCache != null) {
            memoryCache.evictAll();
        }
    }
}
