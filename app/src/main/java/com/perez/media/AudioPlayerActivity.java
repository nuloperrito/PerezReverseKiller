package com.perez.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.perez.revkiller.R;
import com.perez.util.RealFuncUtil;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class AudioPlayerActivity extends AppCompatActivity {

    private static final String TAG = "AudioPlayerActivity";
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 250;

    private MediaPlayer mPlayer;
    private boolean mIsLooping = false;
    private boolean mIsUserTrackingSeek = false;

    // UI Widgets
    private ImageView mCoverView;
    private TextView mTitleView;
    private TextView mArtistView;
    private TextView mAlbumView;
    private TextView mSpecsView;
    private TextView mPositionView;
    private TextView mDurationView;
    private SeekBar mProgress;
    private Button mPlayBtn;
    private Button mLoopBtn;
    private Button mStopBtn;

    private Uri mUri;
    private int mSavedPosition = 0;
    private boolean mShouldPlayWhenPrepared = true;

    // Progress Updater Handler
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mProgressUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPlayer != null && mPlayer.isPlaying() && !mIsUserTrackingSeek) {
                int currentPos = mPlayer.getCurrentPosition();
                mProgress.setProgress(currentPos);
                mPositionView.setText(formatTime(currentPos));
            }
            if (mPlayer != null && mPlayer.isPlaying()) {
                mMainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_player);

        initViews();

        Uri dataUri = getIntent().getData();
        if (dataUri == null) {
            Toast.makeText(this, "Invalid audio URI", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (dataUri.getScheme() == null) {
            dataUri = Uri.fromFile(new File(dataUri.toString()));
        }
        mUri = dataUri;

        String displayName = RealFuncUtil.extractFileNameFromUri(this, mUri);
        setTitle(displayName);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(displayName);
        }

        extractAndDisplayMetadata(mUri, displayName);

        // Check if MediaPlayer is retained from previous orientation instance
        MediaPlayer retainedPlayer = (MediaPlayer) getLastCustomNonConfigurationInstance();
        if (retainedPlayer != null) {
            mPlayer = retainedPlayer;
            bindRetainedPlayer();
        } else {
            if (savedInstanceState != null) {
                mSavedPosition = savedInstanceState.getInt("saved_pos", 0);
                mShouldPlayWhenPrepared = savedInstanceState.getBoolean("is_playing", true);
                mIsLooping = savedInstanceState.getBoolean("is_looping", false);
            }
            updateLoopButtonState();
            initAndPreparePlayer(mUri);
        }
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        // Pass the live MediaPlayer instance to the new Activity instance
        return mPlayer;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("saved_pos", mPlayer != null ? mPlayer.getCurrentPosition() : mSavedPosition);
        outState.putBoolean("is_playing", mPlayer != null && mPlayer.isPlaying());
        outState.putBoolean("is_looping", mIsLooping);
    }

    private void initViews() {
        mCoverView = findViewById(R.id.album_cover);
        mTitleView = findViewById(R.id.track_title);
        mArtistView = findViewById(R.id.track_artist);
        mAlbumView = findViewById(R.id.track_album);
        mSpecsView = findViewById(R.id.track_specs);
        mPositionView = findViewById(R.id.position);
        mDurationView = findViewById(R.id.duration);
        mProgress = findViewById(R.id.seeker);
        mPlayBtn = findViewById(R.id.playbtn);
        mLoopBtn = findViewById(R.id.loopbtn);
        mStopBtn = findViewById(R.id.stopbtn);

        mPlayBtn.setOnClickListener(this::onPlayPauseClicked);
        mStopBtn.setOnClickListener(this::onStopClicked);
        mLoopBtn.setOnClickListener(this::onLoopClicked);

        mProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mPositionView.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsUserTrackingSeek = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mIsUserTrackingSeek = false;
                if (mPlayer != null) {
                    mPlayer.seekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void extractAndDisplayMetadata(Uri uri, String fallbackTitle) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        String title = null;
        String artist = null;
        String album = null;
        String bitrateStr = null;
        byte[] artBytes = null;

        try {
            retriever.setDataSource(this, uri);
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            artBytes = retriever.getEmbeddedPicture();
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract metadata via MediaMetadataRetriever", e);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }

        // Title
        mTitleView.setText((title != null && !title.trim().isEmpty()) ? title : fallbackTitle);

        // Artist & Album
        mArtistView.setText((artist != null && !artist.trim().isEmpty()) ? artist : getString(R.string.audio_unknown_artist));
        mAlbumView.setText((album != null && !album.trim().isEmpty()) ? album : getString(R.string.audio_unknown_album));

        // Album Art Cover
        if (artBytes != null && artBytes.length > 0) {
            Bitmap coverBitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
            if (coverBitmap != null) {
                mCoverView.setImageBitmap(coverBitmap);
            } else {
                mCoverView.setImageResource(R.drawable.empty_album);
            }
        } else {
            mCoverView.setImageResource(R.drawable.empty_album);
        }

        // Technical Audio Specs (Codec, Channels, Sample Rate, Bitrate)
        extractAudioSpecs(uri, bitrateStr);
    }

    private void extractAudioSpecs(Uri uri, @Nullable String bitrateStr) {
        int sampleRate = -1;
        int channelCount = -1;
        String mime = "";

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(this, uri, null);
            int trackCount = extractor.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String trackMime = format.getString(MediaFormat.KEY_MIME);
                if (trackMime != null && trackMime.startsWith("audio/")) {
                    mime = trackMime.replace("audio/", "").toUpperCase(Locale.US);
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to inspect audio tracks via MediaExtractor", e);
        } finally {
            extractor.release();
        }

        StringBuilder specs = new StringBuilder();
        if (!mime.isEmpty()) {
            specs.append(mime);
        }

        if (bitrateStr != null && !bitrateStr.isEmpty()) {
            try {
                int kbps = Integer.parseInt(bitrateStr) / 1000;
                if (specs.length() > 0) specs.append(" • ");
                specs.append(kbps).append(" kbps");
            } catch (NumberFormatException ignored) {
            }
        }

        if (sampleRate > 0) {
            if (specs.length() > 0) specs.append(" • ");
            specs.append(String.format(Locale.US, "%.1f kHz", sampleRate / 1000.0));
        }

        if (channelCount > 0) {
            if (specs.length() > 0) specs.append(" • ");
            if (channelCount == 1) {
                specs.append(getString(R.string.audio_mono));
            } else if (channelCount == 2) {
                specs.append(getString(R.string.audio_stereo));
            } else {
                specs.append(channelCount).append(" ch");
            }
        }

        mSpecsView.setText(specs.toString());
    }

    private void bindRetainedPlayer() {
        if (mPlayer == null) return;

        updateLoopButtonState();
        mPlayer.setLooping(mIsLooping);

        mPlayer.setOnCompletionListener(mp -> {
            if (!mIsLooping) {
                stopProgressUpdates();
                mProgress.setProgress(0);
                mPositionView.setText(formatTime(0));
                mPlayBtn.setText(getString(R.string.audio_play));
            }
        });

        mPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
            Toast.makeText(AudioPlayerActivity.this, "Playback Error (" + what + ")", Toast.LENGTH_SHORT).show();
            stopProgressUpdates();
            mPlayBtn.setText(getString(R.string.audio_play));
            return true;
        });

        int duration = mPlayer.getDuration();
        int currentPos = mPlayer.getCurrentPosition();
        mProgress.setMax(duration);
        mProgress.setProgress(currentPos);
        mDurationView.setText(formatTime(duration));
        mPositionView.setText(formatTime(currentPos));

        if (mPlayer.isPlaying()) {
            mPlayBtn.setText(getString(R.string.audio_pause));
            startProgressUpdates();
        } else {
            mPlayBtn.setText(getString(R.string.audio_play));
            stopProgressUpdates();
        }
    }

    private void initAndPreparePlayer(Uri uri) {
        releasePlayer();
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(this, uri);
            mPlayer.setLooping(mIsLooping);
            mPlayer.setOnPreparedListener(mp -> {
                int duration = mp.getDuration();
                mProgress.setMax(duration);
                mDurationView.setText(formatTime(duration));

                if (mSavedPosition > 0) {
                    mp.seekTo(mSavedPosition);
                    mp.setOnSeekCompleteListener(player -> {
                        mProgress.setProgress(mSavedPosition);
                        mPositionView.setText(formatTime(mSavedPosition));
                        if (mShouldPlayWhenPrepared) {
                            startPlayback();
                        } else {
                            mPlayBtn.setText(getString(R.string.audio_play));
                        }
                    });
                } else {
                    mProgress.setProgress(0);
                    mPositionView.setText(formatTime(0));
                    if (mShouldPlayWhenPrepared) {
                        startPlayback();
                    } else {
                        mPlayBtn.setText(getString(R.string.audio_play));
                    }
                }
            });

            mPlayer.setOnCompletionListener(mp -> {
                if (!mIsLooping) {
                    stopProgressUpdates();
                    mProgress.setProgress(0);
                    mPositionView.setText(formatTime(0));
                    mPlayBtn.setText(getString(R.string.audio_play));
                }
            });

            mPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                Toast.makeText(AudioPlayerActivity.this, "Playback Error (" + what + ")", Toast.LENGTH_SHORT).show();
                stopProgressUpdates();
                mPlayBtn.setText(getString(R.string.audio_play));
                return true;
            });

            mPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e(TAG, "Failed to setDataSource", e);
            Toast.makeText(this, "Failed to load audio file", Toast.LENGTH_SHORT).show();
        }
    }

    private void startProgressUpdates() {
        mMainHandler.removeCallbacks(mProgressUpdateRunnable);
        mMainHandler.post(mProgressUpdateRunnable);
    }

    private void onPlayPauseClicked(View v) {
        if (mPlayer == null) {
            return;
        }
        if (mPlayer.isPlaying()) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }

    private void onStopClicked(View v) {
        if (mPlayer == null) {
            return;
        }
        pausePlayback();
        mPlayer.seekTo(0);
        mProgress.setProgress(0);
        mPositionView.setText(formatTime(0));
    }

    private void onLoopClicked(View v) {
        mIsLooping = !mIsLooping;
        if (mPlayer != null) {
            mPlayer.setLooping(mIsLooping);
        }
        updateLoopButtonState();
    }

    private void updateLoopButtonState() {
        if (mIsLooping) {
            mLoopBtn.setAlpha(1.0f);
            mLoopBtn.setText(getString(R.string.audio_loop_on));
        } else {
            mLoopBtn.setAlpha(0.45f);
            mLoopBtn.setText(getString(R.string.audio_loop_off));
        }
    }

    private void startPlayback() {
        if (mPlayer != null && !mPlayer.isPlaying()) {
            mPlayer.start();
            mPlayBtn.setText(getString(R.string.audio_pause));
            mMainHandler.removeCallbacks(mProgressUpdateRunnable);
            mMainHandler.post(mProgressUpdateRunnable);
        }
    }

    private void pausePlayback() {
        if (mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.pause();
            mPlayBtn.setText(getString(R.string.audio_play));
            stopProgressUpdates();
        }
    }

    private void stopProgressUpdates() {
        mMainHandler.removeCallbacks(mProgressUpdateRunnable);
    }

    private void releasePlayer() {
        stopProgressUpdates();
        if (mPlayer != null) {
            try {
                if (mPlayer.isPlaying()) {
                    mPlayer.stop();
                }
                mPlayer.reset();
                mPlayer.release();
            } catch (Exception ignored) {
            }
            mPlayer = null;
        }
    }

    private String formatTime(int ms) {
        int totalSeconds = Math.max(0, ms / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Do not pause or disrupt playback if the activity is undergoing rotation
        if (isChangingConfigurations()) {
            return;
        }
        if (mPlayer != null && mPlayer.isPlaying()) {
            pausePlayback();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Do not release the player if it is being transferred to the new rotated activity
        if (isChangingConfigurations()) {
            stopProgressUpdates();
            return;
        }
        releasePlayer();
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Intercepted by manifest: activity is not destroyed, playback continues untouched
    }
}