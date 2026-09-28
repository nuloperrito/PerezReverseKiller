package com.perez.media;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.perez.revkiller.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoPlayerActivity extends AppCompatActivity {

    public static class MovieInfo {
        public String displayName;
        public String path;
        public Uri uri;
    }

    private final List<MovieInfo> mPlayList = new ArrayList<>();
    private int mCurrentPositionIndex = -1;

    private VideoView mVideoView;
    private View mControllerLayout;
    private View mTopBar;
    private View mBottomBar;
    private TextView mTopTitleTextView;
    private TextView mPlayedTextView;
    private TextView mDurationTextView;
    private SeekBar mSeekBar;
    private ImageButton mPlayPauseButton;
    private ImageButton mSoundButton;
    private SoundView mSoundView;
    private View mSoundContainer;

    private AudioManager mAudioManager;
    private GestureDetector mGestureDetector;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mIoExecutor = Executors.newSingleThreadExecutor();

    private boolean mIsControllerVisible = true;
    private boolean mIsFullScreen = false;
    private boolean mIsSilent = false;
    private int mSavedVolume = 0;
    private int mSavedPlaybackPosition = 0;
    private String mCurrentVideoTitle = "";

    private static final int AUTO_HIDE_DELAY_MS = 5000;

    private final Runnable mHideControllerRunnable = this::hideControllerAnimated;

    private final Runnable mProgressUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVideoView != null && mVideoView.isPlaying()) {
                int position = mVideoView.getCurrentPosition();
                mSeekBar.setProgress(position);
                mPlayedTextView.setText(formatTime(position));
            }
            mUiHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        if (savedInstanceState != null) {
            mIsFullScreen = savedInstanceState.getBoolean("is_fullscreen", false);
            mSavedPlaybackPosition = savedInstanceState.getInt("playback_position", 0);
            mCurrentPositionIndex = savedInstanceState.getInt("current_index", -1);
            mCurrentVideoTitle = savedInstanceState.getString("video_title", "");
        }

        initViews();
        initAudio();
        initGestures();

        if (mIsFullScreen) {
            applyFullScreen(true);
        }

        resolveIntentAndLoadPlaylist();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("is_fullscreen", mIsFullScreen);
        outState.putInt("playback_position", mVideoView != null ? mVideoView.getCurrentPosition() : 0);
        outState.putInt("current_index", mCurrentPositionIndex);
        outState.putString("video_title", mCurrentVideoTitle);
    }

    private void initViews() {
        mVideoView = findViewById(R.id.vv);
        mControllerLayout = findViewById(R.id.controller_root);
        mTopBar = findViewById(R.id.top_bar);
        mBottomBar = findViewById(R.id.bottom_bar);
        mTopTitleTextView = findViewById(R.id.video_title_fullscreen);
        mPlayedTextView = findViewById(R.id.has_played);
        mDurationTextView = findViewById(R.id.duration);
        mSeekBar = findViewById(R.id.seekbar);
        mPlayPauseButton = findViewById(R.id.button3);
        mSoundButton = findViewById(R.id.button5);
        mSoundView = findViewById(R.id.sound_view);
        mSoundContainer = findViewById(R.id.sound_container);

        // Consume touches on control panels so they do not trigger background screen gestures
        mTopBar.setClickable(true);
        mBottomBar.setClickable(true);
        mSoundContainer.setClickable(true);

        mPlayPauseButton.setOnClickListener(v -> togglePlayPause());

        mSoundButton.setOnClickListener(v -> {
            resetHideTimer();
            toggleSoundView();
        });

        mSoundButton.setOnLongClickListener(v -> {
            toggleMute();
            resetHideTimer();
            return true;
        });

        mSoundView.setOnVolumeChangeListener(volume -> {
            resetHideTimer();
            updateVolume(volume, false);
        });

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mVideoView.seekTo(progress);
                    mPlayedTextView.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mUiHandler.removeCallbacks(mHideControllerRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                resetHideTimer();
            }
        });

        mVideoView.setOnPreparedListener(mp -> {
            int duration = mVideoView.getDuration();
            mSeekBar.setMax(duration);
            mDurationTextView.setText(formatTime(duration));
            mVideoView.start();
            mPlayPauseButton.setImageResource(R.drawable.pause);
            mUiHandler.post(mProgressUpdateRunnable);
            resetHideTimer();
        });

        mVideoView.setOnCompletionListener(mp -> playNextVideo());
        mVideoView.setOnErrorListener((mp, what, extra) -> false);
    }

    private void initAudio() {
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (mAudioManager != null) {
            int current = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mSoundView.setVolumeIndex(current);
            mSavedVolume = current;
            updateSoundButtonAlpha(current);
        }
    }

    private void initGestures() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                // Single tap: toggle controller visibility
                if (mIsControllerVisible) {
                    hideControllerAnimated();
                } else {
                    showControllerAnimated();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                // Double tap: toggle play/pause
                togglePlayPause();
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                // Long press: toggle fullscreen
                toggleFullScreen();
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Pure single-pipeline touch dispatching
        if (mGestureDetector != null && mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void resolveIntentAndLoadPlaylist() {
        Uri directUri = getIntent().getData();
        if (directUri != null) {
            // Normalize schemeless URI to standard file:// URI
            if (directUri.getScheme() == null) {
                directUri = Uri.fromFile(new File(directUri.toString()));
            }

            MovieInfo info = new MovieInfo();
            info.displayName = extractFileNameFromUri(directUri);
            info.path = directUri.getPath();
            info.uri = directUri;
            mPlayList.add(info);
            mCurrentPositionIndex = 0;
            startPlayCurrent();
        }

        // Scan media asynchronously to prevent UI freeze
        mIoExecutor.execute(() -> {
            List<MovieInfo> loaded = queryVideoMedia();
            mUiHandler.post(() -> {
                if (!loaded.isEmpty()) {
                    if (mPlayList.isEmpty()) {
                        mPlayList.addAll(loaded);
                        mCurrentPositionIndex = 0;
                        startPlayCurrent();
                    } else {
                        mPlayList.addAll(loaded);
                    }
                }
            });
        });
    }

    private List<MovieInfo> queryVideoMedia() {
        List<MovieInfo> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATA},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
                int dataIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                int idIndex = cursor.getColumnIndex(MediaStore.Video.Media._ID);
                do {
                    MovieInfo info = new MovieInfo();
                    info.displayName = (nameIndex != -1) ? cursor.getString(nameIndex) : "Video";
                    info.path = (dataIndex != -1) ? cursor.getString(dataIndex) : "";
                    if (idIndex != -1) {
                        long id = cursor.getLong(idIndex);
                        info.uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                    } else if (!TextUtils.isEmpty(info.path)) {
                        info.uri = Uri.fromFile(new File(info.path));
                    }
                    list.add(info);
                } while (cursor.moveToNext());
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    private void startPlayCurrent() {
        if (mCurrentPositionIndex >= 0 && mCurrentPositionIndex < mPlayList.size()) {
            MovieInfo info = mPlayList.get(mCurrentPositionIndex);
            mCurrentVideoTitle = info.displayName;

            updateTitles();

            if (info.uri != null) {
                mVideoView.setVideoURI(info.uri);
            } else if (!TextUtils.isEmpty(info.path)) {
                mVideoView.setVideoPath(info.path);
            }
            mPlayPauseButton.setImageResource(R.drawable.pause);
        }
    }

    private void updateTitles() {
        setTitle(mCurrentVideoTitle);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null && !mIsFullScreen) {
            actionBar.setTitle(mCurrentVideoTitle);
        }
        if (mTopTitleTextView != null) {
            mTopTitleTextView.setText(mCurrentVideoTitle);
        }
    }

    private void playNextVideo() {
        if (++mCurrentPositionIndex < mPlayList.size()) {
            startPlayCurrent();
        } else {
            finish();
        }
    }

    private void togglePlayPause() {
        if (mVideoView.isPlaying()) {
            mVideoView.pause();
            mPlayPauseButton.setImageResource(R.drawable.play);
            showControllerAnimated();
        } else {
            mVideoView.start();
            mPlayPauseButton.setImageResource(R.drawable.pause);
            resetHideTimer();
        }
    }

    private void toggleSoundView() {
        if (mSoundContainer.getVisibility() == View.VISIBLE) {
            mSoundContainer.animate().alpha(0f).setDuration(200).withEndAction(() ->
                    mSoundContainer.setVisibility(View.GONE)).start();
        } else {
            mSoundContainer.setAlpha(0f);
            mSoundContainer.setVisibility(View.VISIBLE);
            mSoundContainer.animate().alpha(1f).setDuration(200).start();
        }
    }

    private void toggleMute() {
        if (mIsSilent) {
            mIsSilent = false;
            mSoundButton.setImageResource(R.drawable.soundenable);
            updateVolume(mSavedVolume > 0 ? mSavedVolume : 1, true);
        } else {
            mIsSilent = true;
            mSavedVolume = mSoundView.getVolumeIndex();
            mSoundButton.setImageResource(R.drawable.sounddisable);
            updateVolume(0, true);
        }
    }

    private void updateVolume(int volume, boolean updateView) {
        if (mAudioManager != null) {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
            if (updateView) {
                mSoundView.setVolumeIndex(volume);
            }
            updateSoundButtonAlpha(volume);
        }
    }

    private void updateSoundButtonAlpha(int volume) {
        int max = mSoundView.getMaxVolume();
        int alpha = (max > 0) ? (volume * (0xCC - 0x55) / max + 0x55) : 0xCC;
        mSoundButton.setAlpha(alpha / 255f);
    }

    private void toggleFullScreen() {
        boolean targetFullScreen = !mIsFullScreen;
        int videoWidth = mVideoView.getVideoWidth();
        int videoHeight = mVideoView.getVideoHeight();

        if (targetFullScreen) {
            if (videoWidth > videoHeight && videoWidth > 0) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        applyFullScreen(targetFullScreen);
        showControllerAnimated();
    }

    private void applyFullScreen(boolean fullScreen) {
        mIsFullScreen = fullScreen;

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ActionBar actionBar = getSupportActionBar();

        if (mIsFullScreen) {
            if (actionBar != null) {
                actionBar.hide();
            }
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

            mTopBar.setVisibility(mIsControllerVisible ? View.VISIBLE : View.GONE);
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
            if (actionBar != null) {
                actionBar.setTitle(mCurrentVideoTitle);
                actionBar.show();
            }
            mTopBar.setVisibility(View.GONE);
        }

        mVideoView.requestLayout();
    }

    private void showControllerAnimated() {
        mUiHandler.removeCallbacks(mHideControllerRunnable);
        mIsControllerVisible = true;

        mControllerLayout.setVisibility(View.VISIBLE);
        mTopBar.setVisibility(mIsFullScreen ? View.VISIBLE : View.GONE);

        mControllerLayout.animate().cancel();
        mControllerLayout.animate().alpha(1.0f).setDuration(250).withEndAction(this::resetHideTimer).start();
    }

    private void hideControllerAnimated() {
        mUiHandler.removeCallbacks(mHideControllerRunnable);
        mIsControllerVisible = false;

        mControllerLayout.animate().cancel();
        mControllerLayout.animate().alpha(0.0f).setDuration(250).withEndAction(() -> {
            mControllerLayout.setVisibility(View.GONE);
            mSoundContainer.setVisibility(View.GONE);
        }).start();
    }

    private void resetHideTimer() {
        mUiHandler.removeCallbacks(mHideControllerRunnable);
        mUiHandler.postDelayed(mHideControllerRunnable, AUTO_HIDE_DELAY_MS);
    }

    private String formatTime(int ms) {
        int totalSeconds = Math.max(0, ms / 1000);
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String extractFileNameFromUri(Uri uri) {
        if (uri == null) return "Video";

        String scheme = uri.getScheme();

        // Case 1: content:// scheme (query MediaStore displayName)
        if ("content".equalsIgnoreCase(scheme)) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(
                        uri,
                        new String[]{MediaStore.MediaColumns.DISPLAY_NAME},
                        null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String name = cursor.getString(nameIndex);
                        if (!TextUtils.isEmpty(name)) {
                            return name;
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        // Case 2: file:// scheme or schemeless raw path (e.g. Uri.parse(file.toString()))
        String path = uri.getPath();
        if (TextUtils.isEmpty(path)) {
            path = uri.toString();
        }

        if (!TextUtils.isEmpty(path)) {
            try {
                String decodedPath = Uri.decode(path);
                File file = new File(decodedPath);
                String name = file.getName();
                if (!TextUtils.isEmpty(name)) {
                    return name;
                }
            } catch (Exception ignored) {
            }
        }

        // Case 3: Fallback to last path segment
        String lastSegment = uri.getLastPathSegment();
        if (!TextUtils.isEmpty(lastSegment)) {
            return Uri.decode(lastSegment);
        }

        return "Video";
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Keep system bars hidden when orientation change finishes in fullscreen
        if (mIsFullScreen) {
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }
        }
        mVideoView.requestLayout();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSavedPlaybackPosition = mVideoView.getCurrentPosition();
        if (mVideoView.isPlaying()) {
            mVideoView.pause();
            mPlayPauseButton.setImageResource(R.drawable.play);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSavedPlaybackPosition > 0) {
            mVideoView.seekTo(mSavedPlaybackPosition);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUiHandler.removeCallbacksAndMessages(null);
        mIoExecutor.shutdown();
        mVideoView.stopPlayback();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mIsFullScreen) {
                toggleFullScreen();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}