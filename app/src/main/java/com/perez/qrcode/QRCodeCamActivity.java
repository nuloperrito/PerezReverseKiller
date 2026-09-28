package com.perez.qrcode;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.perez.revkiller.R;
import com.perez.util.RealFuncUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class QRCodeCamActivity extends AppCompatActivity {

    private PreviewView previewView;
    private MediaPlayer mediaPlayer;
    private ExecutorService cameraExecutor;
    private static final float BEEP_VOLUME = 0.50f;
    private static final long VIBRATE_DURATION = 200L;
    private static final int PERMISSION_REQUEST_CAMERA = 1001;
    private final AtomicBoolean isDialogShowing = new AtomicBoolean(false);

    private void initBeepSound() {
        if (shouldPlayBeep() && mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(mp -> mp.seekTo(0));
            loadBeepSound();
        }
    }

    private void loadBeepSound() {
        try (AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep)) {
            if (file != null && mediaPlayer != null) {
                mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            }
        } catch (IOException ignored) {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = null;
        }
    }

    private boolean shouldPlayBeep() {
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        return audioService != null && audioService.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
    }

    private void playBeepSoundAndVibrate() {
        if (shouldPlayBeep() && mediaPlayer != null) {
            mediaPlayer.start();
        }
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_DURATION, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(VIBRATE_DURATION);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrcode);

        previewView = findViewById(R.id.preview_qrcode);
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
        } else {
            startCamera();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initBeepSound();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void showResult(String result) {
        if (isFinishing() || isDestroyed()) {
            isDialogShowing.set(false);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(QRCodeCamActivity.this);
        builder.setTitle(R.string.scan_result_title);
        builder.setMessage(result);

        // Reset flag on button click or dismiss
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> isDialogShowing.set(false));
        builder.setOnCancelListener(dialog -> isDialogShowing.set(false));
        builder.setOnDismissListener(dialog -> isDialogShowing.set(false));

        builder.setNeutralButton(R.string.copy, (dlg, which) -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("QrcodeResult", result);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(QRCodeCamActivity.this, R.string.copied_tips, Toast.LENGTH_LONG).show();
            }
            isDialogShowing.set(false);
        });

        builder.create().show();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Force full screen center fill
                previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

                // Use standard 16:9 aspect ratio to avoid black borders and unsupported resolutions
                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // Run analysis on dedicated background thread, NEVER on main thread
                imageAnalysis.setAnalyzer(cameraExecutor, new QrCodeAnalyzer());

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(QRCodeCamActivity.this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                RealFuncUtil.showDlgMsg(this, getString(R.string.scan_qr_failed), RealFuncUtil.getFullException(e), null);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private class QrCodeAnalyzer implements ImageAnalysis.Analyzer {
        private final MultiFormatReader reader = new MultiFormatReader();

        QrCodeAnalyzer() {
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            reader.setHints(hints);
        }

        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            // Drop frames immediately if dialog is currently displayed
            if (isDialogShowing.get()) {
                imageProxy.close();
                return;
            }

            try {
                ImageProxy.PlaneProxy plane = imageProxy.getPlanes()[0];
                ByteBuffer buffer = plane.getBuffer();
                int rowStride = plane.getRowStride();
                int width = imageProxy.getWidth();
                int height = imageProxy.getHeight();
                int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);

                // Strip row padding bytes and rotate image data according to sensor orientation
                byte[] rotatedData;
                int rotatedWidth;
                int rotatedHeight;

                if (rotationDegrees == 90) {
                    rotatedData = rotateYUV90(data, width, height, rowStride);
                    rotatedWidth = height;
                    rotatedHeight = width;
                } else if (rotationDegrees == 270) {
                    rotatedData = rotateYUV270(data, width, height, rowStride);
                    rotatedWidth = height;
                    rotatedHeight = width;
                } else {
                    rotatedData = stripStride(data, width, height, rowStride);
                    rotatedWidth = width;
                    rotatedHeight = height;
                }

                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                        rotatedData, rotatedWidth, rotatedHeight, 0, 0, rotatedWidth, rotatedHeight, false);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                Result result = reader.decodeWithState(bitmap);
                if (result != null) {
                    // Atomically guarantee only one execution passes before dialog is dismissed
                    if (isDialogShowing.compareAndSet(false, true)) {
                        final String text = result.getText();
                        runOnUiThread(() -> {
                            playBeepSoundAndVibrate();
                            showResult(text);
                        });
                    }
                }
            } catch (NotFoundException ignored) {
                // Expected when frame contains no QR code
            } catch (Exception ignored) {
                // Ignore other decode parsing exceptions
            } finally {
                reader.reset();
                imageProxy.close();
            }
        }

        private byte[] rotateYUV90(byte[] data, int width, int height, int rowStride) {
            byte[] rotated = new byte[width * height];
            for (int y = 0; y < height; y++) {
                int srcRowOffset = y * rowStride;
                for (int x = 0; x < width; x++) {
                    rotated[x * height + (height - 1 - y)] = data[srcRowOffset + x];
                }
            }
            return rotated;
        }

        private byte[] rotateYUV270(byte[] data, int width, int height, int rowStride) {
            byte[] rotated = new byte[width * height];
            for (int y = 0; y < height; y++) {
                int srcRowOffset = y * rowStride;
                for (int x = 0; x < width; x++) {
                    rotated[(width - 1 - x) * height + y] = data[srcRowOffset + x];
                }
            }
            return rotated;
        }

        private byte[] stripStride(byte[] data, int width, int height, int rowStride) {
            if (rowStride == width) {
                return data;
            }
            byte[] stripped = new byte[width * height];
            for (int y = 0; y < height; y++) {
                System.arraycopy(data, y * rowStride, stripped, y * width, width);
            }
            return stripped;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Please grant camera permissions before using the module.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}