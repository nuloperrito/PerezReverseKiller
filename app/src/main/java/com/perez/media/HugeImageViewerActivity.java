package com.perez.media;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.Movie;
import android.graphics.Point;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import com.perez.revkiller.R;
import com.perez.exifremover.Interfaz;
import com.perez.util.RealFuncUtil;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class HugeImageViewerActivity extends AppCompatActivity {

    private static final String KEY_URI = "SAVED_URI";
    private static final String KEY_NORM_X = "SAVED_NORM_X";
    private static final String KEY_NORM_Y = "SAVED_NORM_Y";
    private static final String KEY_OUTER_SCALE = "SAVED_OUTER_SCALE";

    private TileDrawable mTileDrawable;
    private Drawable mGifDrawable;
    private PinchImageView mPinchImageView;
    private ImageButton mIbtn;
    private Uri mUri;

    private float mSavedNormX = 0.5f;
    private float mSavedNormY = 0.5f;
    private float mSavedOuterScale = 1.0f;
    private boolean mHasSavedState = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_image_viewer);

        mPinchImageView = (PinchImageView) findViewById(R.id.pic);
        mIbtn = (ImageButton) findViewById(R.id.verEXIF);

        // Resolve Uri from Intent or SavedState
        if (savedInstanceState != null) {
            mUri = savedInstanceState.getParcelable(KEY_URI);
            mSavedNormX = savedInstanceState.getFloat(KEY_NORM_X, 0.5f);
            mSavedNormY = savedInstanceState.getFloat(KEY_NORM_Y, 0.5f);
            mSavedOuterScale = savedInstanceState.getFloat(KEY_OUTER_SCALE, 1.0f);
            mHasSavedState = mSavedOuterScale > 1.001f;
        }

        if (mUri == null) mUri = getIntent().getData();
        if (mUri == null) {
            Toast.makeText(this, "Invalid image URI", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (mUri.getScheme() == null) {
            mUri = Uri.fromFile(new File(mUri.getPath()));
        }
        if (mUri == null) {
            Toast.makeText(this, "Invalid image URI", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // EXIF button configuration
        mIbtn.setOnClickListener(v -> {
            try {
                String realPath = resolveExifPath(HugeImageViewerActivity.this, mUri);
                AlertDialog alg = new AlertDialog.Builder(HugeImageViewerActivity.this)
                        .setTitle("EXIF Information")
                        .setMessage(Interfaz.showExif(realPath))
                        .setPositiveButton(android.R.string.ok, null)
                        .create();
                alg.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                alg.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        String displayName = RealFuncUtil.extractFileNameFromUri(this, mUri);
        setTitle(displayName);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(displayName);
        }

        // Branch: GIF vs Static Mega Image
        if (isGifFormat(this, mUri)) {
            loadGifImage(mUri);
        } else {
            loadStaticHugeImage(mUri);
        }
    }

    private void loadStaticHugeImage(final Uri uri) {
        mPinchImageView.post(new Runnable() {
            @Override
            public void run() {
                mTileDrawable = new TileDrawable();
                mTileDrawable.setInitCallback(new TileDrawable.InitCallback() {
                    @Override
                    public void onInit() {
                        mPinchImageView.setImageDrawable(mTileDrawable);
                        restorePendingViewportState();
                    }
                });
                mTileDrawable.init(new HugeImageRegionLoader(HugeImageViewerActivity.this, uri),
                        new Point(mPinchImageView.getWidth(), mPinchImageView.getHeight()));
            }
        });
    }

    private void loadGifImage(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Drawable decoded = null;
                // API 28+: ImageDecoder natively supports hardware-accelerated animated GIF/WebP
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                        decoded = ImageDecoder.decodeDrawable(source, new ImageDecoder.OnHeaderDecodedListener() {
                            @Override
                            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info, ImageDecoder.Source source) {
                                Size size = info.getSize();
                                int maxDim = Math.max(size.getWidth(), size.getHeight());
                                if (maxDim > 4096) {
                                    decoder.setTargetSampleSize(maxDim / 4096 + 1);
                                }
                            }
                        });
                        if (decoded instanceof AnimatedImageDrawable) {
                            ((AnimatedImageDrawable) decoded).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                            ((AnimatedImageDrawable) decoded).start();
                        }
                    } catch (Throwable t) {
                        decoded = null;
                    }
                }

                // Fallback for API 23-27 or if ImageDecoder failed
                if (decoded == null) {
                    InputStream is = null;
                    try {
                        is = getContentResolver().openInputStream(uri);
                        if (is != null) {
                            byte[] buffer = new byte[16384];
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                baos.write(buffer, 0, len);
                            }
                            byte[] bytes = baos.toByteArray();
                            Movie movie = Movie.decodeByteArray(bytes, 0, bytes.length);
                            if (movie != null) {
                                MovieDrawable movieDrawable = new MovieDrawable(movie);
                                movieDrawable.start();
                                decoded = movieDrawable;
                            }
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    } finally {
                        if (is != null) {
                            try { is.close(); } catch (Exception ignored) {}
                        }
                    }
                }

                final Drawable result = decoded;
                mPinchImageView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                            if (result instanceof MovieDrawable) {
                                ((MovieDrawable) result).recycle();
                            }
                            return;
                        }
                        if (result != null) {
                            mGifDrawable = result;
                            mPinchImageView.setImageDrawable(result);
                            restorePendingViewportState();
                        }
                    }
                });
            }
        }).start();
    }

    private void restorePendingViewportState() {
        if (mHasSavedState) {
            mPinchImageView.restoreScaleAndCenter(mSavedNormX, mSavedNormY, mSavedOuterScale);
            mHasSavedState = false;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mUri != null) {
            outState.putParcelable(KEY_URI, mUri);
        }
        if (mPinchImageView != null && mPinchImageView.isReady()) {
            float[] center = mPinchImageView.getImageCenterNormalized();
            outState.putFloat(KEY_NORM_X, center[0]);
            outState.putFloat(KEY_NORM_Y, center[1]);
            outState.putFloat(KEY_OUTER_SCALE, mPinchImageView.getOuterScale());
        }
    }

    private boolean isGifFormat(Context context, Uri uri) {
        if (uri == null) return false;
        String type = context.getContentResolver().getType(uri);
        if ("image/gif".equalsIgnoreCase(type)) return true;
        String path = uri.getPath();
        if (path != null && path.toLowerCase().endsWith(".gif")) return true;

        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            if (is != null) {
                byte[] header = new byte[3];
                int read = is.read(header);
                if (read == 3 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
                    return true;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
        }
        return false;
    }

    private String resolveExifPath(Context context, Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme()) || uri.getScheme() == null) {
            return uri.getPath();
        }
        File tempFile = new File(context.getCacheDir(), "temp_exif_" + System.currentTimeMillis());
        InputStream is = null;
        FileOutputStream os = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            if (is != null) {
                os = new FileOutputStream(tempFile);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    os.write(buffer, 0, len);
                }
                return tempFile.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { if (os != null) os.close(); } catch (Exception ignored) {}
        }
        return uri.getPath();
    }

    @Override
    protected void onDestroy() {
        if (mTileDrawable != null) {
            mTileDrawable.recycle();
            mTileDrawable = null;
        }
        if (mGifDrawable instanceof MovieDrawable) {
            ((MovieDrawable) mGifDrawable).recycle();
            mGifDrawable = null;
        } else if (mGifDrawable instanceof Animatable) {
            ((Animatable) mGifDrawable).stop();
            mGifDrawable = null;
        }
        super.onDestroy();
    }
}