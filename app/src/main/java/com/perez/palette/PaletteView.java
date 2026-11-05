package com.perez.palette;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class PaletteView extends View {
    private Paint mPaint;
    private Path mPath;
    private float mLastX;
    private float mLastY;
    private float mCurveEndX;
    private float mCurveEndY;

    private Bitmap mBufferBitmap;
    private Canvas mBufferCanvas;

    private static final int MAX_CACHE_STEP = 20;
    private List<DrawingInfo> mDrawingList;
    private List<DrawingInfo> mRemovedList;

    private Xfermode mXferModeClear;
    private Xfermode mXferModeDraw;
    private int mDrawSize;
    private int mEraserSize;
    private int mPenColor = 0xFF000000;
    private int mPenAlpha = 255;
    private boolean mCanEraser;
    private Callback mCallback;

    private Matrix mMatrix;
    private Matrix mInverseMatrix;
    private float[] mMatrixValues;
    private ScaleGestureDetector mScaleDetector;
    private float mLastFocusX;
    private float mLastFocusY;

    // Gesture and drawing state machine variables
    private boolean mIsMultiTouch = false;
    private boolean mHasDrawn = false;
    private boolean mFromMultiTouch = false;

    private static final float MIN_SCALE = 0.2f;
    private static final float MAX_SCALE = 8.0f;

    private boolean mIsModified = false;

    public enum Mode {
        DRAW,
        ERASER
    }

    private Mode mMode = Mode.DRAW;

    public PaletteView(Context context) {
        super(context);
        init(context);
    }

    public PaletteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PaletteView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public interface Callback {
        void onUndoRedoStatusChanged();
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    private void init(Context context) {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setFilterBitmap(true);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mDrawSize = DimenUtils.dp2pxInt(3);
        mEraserSize = DimenUtils.dp2pxInt(30);
        mPaint.setStrokeWidth(mDrawSize);
        mPaint.setColor(mPenColor);
        mXferModeDraw = new PorterDuffXfermode(PorterDuff.Mode.SRC);
        mXferModeClear = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
        mPaint.setXfermode(mXferModeDraw);

        mDrawingList = new ArrayList<>(MAX_CACHE_STEP);
        mRemovedList = new ArrayList<>(MAX_CACHE_STEP);

        mMatrix = new Matrix();
        mInverseMatrix = new Matrix();
        mMatrixValues = new float[9];

        mScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                mMatrix.getValues(mMatrixValues);
                float currentScale = mMatrixValues[Matrix.MSCALE_X];
                float targetScale = currentScale * scaleFactor;

                if (targetScale < MIN_SCALE) {
                    scaleFactor = MIN_SCALE / currentScale;
                } else if (targetScale > MAX_SCALE) {
                    scaleFactor = MAX_SCALE / currentScale;
                }

                mMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                mMatrix.invert(mInverseMatrix);
                return true;
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            mBufferBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mBufferCanvas = new Canvas(mBufferBitmap);
            reDraw();
        }
    }

    private abstract static class DrawingInfo {
        Paint paint;
        abstract void draw(Canvas canvas);
    }

    private static class PathDrawingInfo extends DrawingInfo {
        Path path;

        @Override
        void draw(Canvas canvas) {
            canvas.drawPath(path, paint);
        }
    }

    public Mode getMode() {
        return mMode;
    }

    public void setMode(Mode mode) {
        if (mode != mMode) {
            mMode = mode;
            if (mMode == Mode.DRAW) {
                mPaint.setXfermode(mXferModeDraw);
                mPaint.setColor(mPenColor);
                mPaint.setAlpha(mPenAlpha);
            } else {
                mPaint.setXfermode(mXferModeClear);
            }
            updatePaintStrokeWidthForCurrentScale();
        }
    }

    public void setEraserSize(int size) {
        mEraserSize = size;
        if (mMode == Mode.ERASER) {
            updatePaintStrokeWidthForCurrentScale();
        }
    }

    public void setPenRawSize(int size) {
        mDrawSize = size;
        if (mMode == Mode.DRAW) {
            updatePaintStrokeWidthForCurrentScale();
        }
    }

    public void setPenColor(int color) {
        mPenColor = color;
        mPaint.setColor(color);
    }

    public int getPenColor() {
        return mPenColor;
    }

    public int getPenSize() {
        return mDrawSize;
    }

    public int getEraserSize() {
        return mEraserSize;
    }

    public void setPenAlpha(int alpha) {
        mPenAlpha = alpha;
        if (mMode == Mode.DRAW) {
            mPaint.setAlpha(alpha);
        }
    }

    public int getPenAlpha() {
        return mPenAlpha;
    }

    private float getCurrentScale() {
        mMatrix.getValues(mMatrixValues);
        return mMatrixValues[Matrix.MSCALE_X];
    }

    private void updatePaintStrokeWidthForCurrentScale() {
        float currentScale = getCurrentScale();
        if (currentScale <= 0) currentScale = 1.0f;
        float baseSize = (mMode == Mode.DRAW) ? mDrawSize : mEraserSize;
        mPaint.setStrokeWidth(baseSize / currentScale);
    }

    private void reDraw() {
        if (mBufferBitmap == null) return;
        mBufferBitmap.eraseColor(Color.TRANSPARENT);
        mBufferCanvas.save();
        mBufferCanvas.concat(mMatrix);
        if (mDrawingList != null) {
            for (DrawingInfo drawingInfo : mDrawingList) {
                drawingInfo.draw(mBufferCanvas);
            }
        }
        mBufferCanvas.restore();
        invalidate();
    }

    public boolean canRedo() {
        return mRemovedList != null && mRemovedList.size() > 0;
    }

    public boolean canUndo() {
        return mDrawingList != null && mDrawingList.size() > 0;
    }

    public void redo() {
        int size = mRemovedList == null ? 0 : mRemovedList.size();
        if (size > 0) {
            DrawingInfo info = mRemovedList.remove(size - 1);
            mDrawingList.add(info);
            mCanEraser = true;
            mIsModified = true;
            reDraw();
            if (mCallback != null) {
                mCallback.onUndoRedoStatusChanged();
            }
        }
    }

    public void undo() {
        int size = mDrawingList == null ? 0 : mDrawingList.size();
        if (size > 0) {
            DrawingInfo info = mDrawingList.remove(size - 1);
            if (mRemovedList == null) {
                mRemovedList = new ArrayList<>(MAX_CACHE_STEP);
            }
            if (size == 1) {
                mCanEraser = false;
            }
            mRemovedList.add(info);
            mIsModified = true;
            reDraw();
            if (mCallback != null) {
                mCallback.onUndoRedoStatusChanged();
            }
        }
    }

    public void clear() {
        if (mBufferBitmap != null) {
            boolean hadDrawing = (mDrawingList != null && !mDrawingList.isEmpty());
            if (mDrawingList != null) {
                mDrawingList.clear();
            }
            if (mRemovedList != null) {
                mRemovedList.clear();
            }
            mCanEraser = false;
            if (hadDrawing) {
                mIsModified = true;
            }
            mBufferBitmap.eraseColor(Color.TRANSPARENT);
            invalidate();
            if (mCallback != null) {
                mCallback.onUndoRedoStatusChanged();
            }
        }
    }

    public boolean isModified() {
        return mIsModified;
    }

    public void setModified(boolean modified) {
        mIsModified = modified;
    }

    public Bitmap buildBitmap() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        if (getBackground() != null) {
            getBackground().setBounds(0, 0, w, h);
            getBackground().draw(canvas);
        } else {
            canvas.drawColor(Color.WHITE);
        }
        if (mBufferBitmap != null) {
            canvas.drawBitmap(mBufferBitmap, 0, 0, null);
        }
        return result;
    }

    private void saveDrawingPath() {
        if (mDrawingList == null) {
            mDrawingList = new ArrayList<>(MAX_CACHE_STEP);
        } else if (mDrawingList.size() == MAX_CACHE_STEP) {
            mDrawingList.remove(0);
        }
        Path cachePath = new Path(mPath);
        Paint cachePaint = new Paint(mPaint);
        PathDrawingInfo info = new PathDrawingInfo();
        info.path = cachePath;
        info.paint = cachePaint;
        mDrawingList.add(info);
        if (mRemovedList != null) {
            mRemovedList.clear();
        }
        mCanEraser = true;
        mIsModified = true;
        if (mCallback != null) {
            mCallback.onUndoRedoStatusChanged();
        }
    }

    private float[] mapScreenToCanvas(float x, float y) {
        float[] pts = new float[]{x, y};
        mInverseMatrix.mapPoints(pts);
        return pts;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBufferBitmap != null) {
            canvas.drawBitmap(mBufferBitmap, 0, 0, null);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        final int action = event.getActionMasked();
        final int pointerCount = event.getPointerCount();

        // Two-finger and multi-touch gestures: Scaling and panning
        if (pointerCount >= 2) {
            if (!mIsMultiTouch) {
                mIsMultiTouch = true;
                // If drawing occurred before switching to multi-touch, finalize the last stroke segment
                if (mHasDrawn && mPath != null) {
                    mPath.lineTo(mLastX, mLastY);
                    Path endSegment = new Path();
                    endSegment.moveTo(mCurveEndX, mCurveEndY);
                    endSegment.lineTo(mLastX, mLastY);

                    if (mBufferBitmap != null) {
                        mBufferCanvas.save();
                        mBufferCanvas.concat(mMatrix);
                        mBufferCanvas.drawPath(endSegment, mPaint);
                        mBufferCanvas.restore();
                    }

                    if (mMode == Mode.DRAW || mCanEraser) {
                        saveDrawingPath();
                    }
                    mPath.reset();
                    mHasDrawn = false;
                } else {
                    // Pure multi-touch start with no previous drawing, reset invalid points
                    if (mPath != null) {
                        mPath.reset();
                    }
                    reDraw();
                }
            }

            mScaleDetector.onTouchEvent(event);

            float sumX = 0;
            float sumY = 0;
            for (int i = 0; i < pointerCount; i++) {
                sumX += event.getX(i);
                sumY += event.getY(i);
            }
            float focusX = sumX / pointerCount;
            float focusY = sumY / pointerCount;

            if (action == MotionEvent.ACTION_MOVE) {
                float dx = focusX - mLastFocusX;
                float dy = focusY - mLastFocusY;
                mMatrix.postTranslate(dx, dy);
                mMatrix.invert(mInverseMatrix);
                reDraw();
            }

            mLastFocusX = focusX;
            mLastFocusY = focusY;
            return true;
        }

        // Lifting one finger: Handling focus reset or smooth transition back to single-finger drawing mode
        if (action == MotionEvent.ACTION_POINTER_UP) {
            int actionIndex = event.getActionIndex();
            int remaining = pointerCount - 1;
            if (remaining == 1) {
                // Only one finger remaining: Anchor its current coordinates as the new drawing start point, immediately releasing multi-touch lock
                int remainingIndex = (actionIndex == 0) ? 1 : 0;
                float[] pts = mapScreenToCanvas(event.getX(remainingIndex), event.getY(remainingIndex));
                updatePaintStrokeWidthForCurrentScale();
                mLastX = pts[0];
                mLastY = pts[1];
                mCurveEndX = pts[0];
                mCurveEndY = pts[1];
                if (mPath == null) {
                    mPath = new Path();
                } else {
                    mPath.reset();
                }
                mPath.moveTo(pts[0], pts[1]);
                mHasDrawn = false;
                mFromMultiTouch = true;
                mIsMultiTouch = false;
                return true;
            } else if (remaining > 1) {
                float sumX = 0;
                float sumY = 0;
                for (int i = 0; i < pointerCount; i++) {
                    if (i == actionIndex) continue;
                    sumX += event.getX(i);
                    sumY += event.getY(i);
                }
                mLastFocusX = sumX / remaining;
                mLastFocusY = sumY / remaining;
                return true;
            }
        }

        // Multi-touch protection
        if (mIsMultiTouch) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mIsMultiTouch = false;
                if (mPath != null) {
                    mPath.reset();
                }
                mHasDrawn = false;
                mFromMultiTouch = false;
            }
            return true;
        }

        // Single-finger touch: Map screen coordinates to world coordinates
        float[] canvasPts = mapScreenToCanvas(event.getX(), event.getY());
        final float x = canvasPts[0];
        final float y = canvasPts[1];

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mFromMultiTouch = false;
                mHasDrawn = false;
                updatePaintStrokeWidthForCurrentScale();
                mLastX = x;
                mLastY = y;
                mCurveEndX = x;
                mCurveEndY = y;
                if (mPath == null) {
                    mPath = new Path();
                } else {
                    mPath.reset();
                }
                mPath.moveTo(x, y);
                break;

            case MotionEvent.ACTION_MOVE:
                if (mBufferBitmap == null) {
                    break;
                }
                if (mMode == Mode.ERASER && !mCanEraser) {
                    break;
                }
                if (mPath == null) {
                    mPath = new Path();
                    mPath.moveTo(x, y);
                    mCurveEndX = x;
                    mCurveEndY = y;
                }

                mHasDrawn = true;
                float endX = (x + mLastX) / 2.0f;
                float endY = (y + mLastY) / 2.0f;
                mPath.quadTo(mLastX, mLastY, endX, endY);

                Path segment = new Path();
                segment.moveTo(mCurveEndX, mCurveEndY);
                segment.quadTo(mLastX, mLastY, endX, endY);

                mBufferCanvas.save();
                mBufferCanvas.concat(mMatrix);
                mBufferCanvas.drawPath(segment, mPaint);
                mBufferCanvas.restore();
                invalidate();

                mCurveEndX = endX;
                mCurveEndY = endY;
                mLastX = x;
                mLastY = y;
                break;

            case MotionEvent.ACTION_UP:
                if (mBufferBitmap != null && mPath != null) {
                    if (!mFromMultiTouch || mHasDrawn) {
                        mPath.lineTo(x, y);
                        Path endSegment = new Path();
                        endSegment.moveTo(mCurveEndX, mCurveEndY);
                        endSegment.lineTo(x, y);

                        mBufferCanvas.save();
                        mBufferCanvas.concat(mMatrix);
                        mBufferCanvas.drawPath(endSegment, mPaint);
                        mBufferCanvas.restore();

                        if (mMode == Mode.DRAW || mCanEraser) {
                            saveDrawingPath();
                        }
                    }
                    mPath.reset();
                    mHasDrawn = false;
                    mFromMultiTouch = false;
                    mIsMultiTouch = false;
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                if (mPath != null) {
                    mPath.reset();
                }
                mHasDrawn = false;
                mFromMultiTouch = false;
                mIsMultiTouch = false;
                reDraw();
                break;
        }
        return true;
    }
}
