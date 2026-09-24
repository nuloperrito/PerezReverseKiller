package com.perez.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.media.AudioManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.perez.revkiller.R;

public class SoundView extends View {
    private Bitmap mBmActive;
    private Bitmap mBmInactive;
    private int mBitmapWidth;
    private int mBitmapHeight;

    private int mCurrentIndex = 0;
    private int mMaxIndex = 15;
    private OnVolumeChangedListener mListener;

    public interface OnVolumeChangedListener {
        void onVolumeChanged(int volume);
    }

    public SoundView(Context context) {
        super(context);
        init(context);
    }

    public SoundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SoundView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mBmActive = BitmapFactory.decodeResource(context.getResources(), R.drawable.sound_line);
        mBmInactive = BitmapFactory.decodeResource(context.getResources(), R.drawable.sound_line1);
        mBitmapWidth = mBmActive.getWidth();
        mBitmapHeight = mBmActive.getHeight();

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            mMaxIndex = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (mMaxIndex <= 0) mMaxIndex = 15;
            mCurrentIndex = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
    }

    public void setOnVolumeChangeListener(OnVolumeChangedListener l) {
        mListener = l;
    }

    public void setVolumeIndex(int volume) {
        int clamped = Math.max(0, Math.min(volume, mMaxIndex));
        if (mCurrentIndex != clamped) {
            mCurrentIndex = clamped;
            invalidate();
        }
    }

    public int getVolumeIndex() {
        return mCurrentIndex;
    }

    public int getMaxVolume() {
        return mMaxIndex;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = mBitmapWidth + getPaddingLeft() + getPaddingRight();
        int stepSpacing = 2;
        int desiredHeight = mMaxIndex * (mBitmapHeight + stepSpacing) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float y = event.getY() - getPaddingTop();
            float contentHeight = getHeight() - getPaddingTop() - getPaddingBottom();
            if (contentHeight <= 0) return true;

            float ratio = 1.0f - (y / contentHeight);
            int newVolume = Math.round(ratio * mMaxIndex);
            newVolume = Math.max(0, Math.min(newVolume, mMaxIndex));

            if (newVolume != mCurrentIndex) {
                mCurrentIndex = newVolume;
                invalidate();
                if (mListener != null) {
                    mListener.onVolumeChanged(mCurrentIndex);
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int availableHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        if (mMaxIndex <= 0 || availableHeight <= 0) return;

        float stepHeight = (float) availableHeight / mMaxIndex;
        int left = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - mBitmapWidth) / 2;

        Rect srcRect = new Rect(0, 0, mBitmapWidth, mBitmapHeight);
        for (int i = 0; i < mMaxIndex; i++) {
            // Draw from top to bottom: highest level first
            int level = mMaxIndex - i;
            Bitmap targetBitmap = (level <= mCurrentIndex) ? mBmActive : mBmInactive;

            int top = getPaddingTop() + (int) (i * stepHeight + (stepHeight - mBitmapHeight) / 2);
            Rect destRect = new Rect(left, top, left + mBitmapWidth, top + mBitmapHeight);
            canvas.drawBitmap(targetBitmap, srcRect, destRect, null);
        }
    }
}