package com.perez.media;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

public class MovieDrawable extends Drawable implements Animatable, Runnable {

    private final Movie mMovie;
    private final int mIntrinsicWidth;
    private final int mIntrinsicHeight;
    private final Bitmap mBitmap;
    private final Canvas mCanvas;
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect mDstRect;
    private final int mDuration;

    private long mMovieStart = 0;
    private boolean mRunning = false;

    public MovieDrawable(Movie movie) {
        mMovie = movie;
        mIntrinsicWidth = Math.max(1, movie.width());
        mIntrinsicHeight = Math.max(1, movie.height());
        mDuration = movie.duration() > 0 ? movie.duration() : 1000;
        mDstRect = new Rect(0, 0, mIntrinsicWidth, mIntrinsicHeight);

        // Downsample backing buffer for huge GIFs to prevent OOM
        int sample = 1;
        int maxDim = Math.max(mIntrinsicWidth, mIntrinsicHeight);
        while (maxDim / sample > 2048) {
            sample *= 2;
        }

        int bmpW = Math.max(1, mIntrinsicWidth / sample);
        int bmpH = Math.max(1, mIntrinsicHeight / sample);
        mBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
        if (sample != 1) {
            float s = 1f / sample;
            mCanvas.scale(s, s);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    @Override
    public void draw(Canvas canvas) {
        if (mRunning) {
            long now = SystemClock.uptimeMillis();
            if (mMovieStart == 0) {
                mMovieStart = now;
            }
            int relTime = (int) ((now - mMovieStart) % mDuration);
            mMovie.setTime(relTime);

            mBitmap.eraseColor(Color.TRANSPARENT);
            mMovie.draw(mCanvas, 0, 0);

            scheduleSelf(this, now + 16);
        }
        canvas.drawBitmap(mBitmap, null, mDstRect, mPaint);
    }

    @Override
    public void run() {
        invalidateSelf();
    }

    @Override
    public void start() {
        if (!mRunning) {
            mRunning = true;
            mMovieStart = 0;
            invalidateSelf();
        }
    }

    @Override
    public void stop() {
        if (mRunning) {
            mRunning = false;
            unscheduleSelf(this);
        }
    }

    @Override
    public boolean isRunning() {
        return mRunning;
    }

    public void recycle() {
        stop();
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}