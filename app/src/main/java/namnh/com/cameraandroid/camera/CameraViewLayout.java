package namnh.com.cameraandroid.camera;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.FrameLayout;

abstract class CameraViewLayout extends FrameLayout {

    private static final float MIN_SPACING_DELTA = 10.0f;
    private float fingerSpacing;
    private GestureDetector gestureDetector;

    public CameraViewLayout(@NonNull Context context) {
        this(context, null);
    }

    public CameraViewLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraViewLayout(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        gestureDetector =
                new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        onTapToFocus(e.getX() / (float) getWidth(), e.getY() / (float) getHeight());
                        return true;
                    }
                });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        int pointerCount = event.getPointerCount();
        if (pointerCount != 2) {
            fingerSpacing = 0;
            return true;
        }
        int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
                fingerSpacing = getFingerSpacing(event);
                break;
            case MotionEvent.ACTION_MOVE:
                handleZoom(fingerSpacing, getFingerSpacing(event));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                fingerSpacing = 0;
                break;
            default:
                break;
        }
        return true;
    }

    private void handleZoom(float oldSpacing, float newSpacing) {
        if (Math.abs(newSpacing - oldSpacing) < MIN_SPACING_DELTA) return;
        pinchToZoom(oldSpacing, newSpacing);
        fingerSpacing = newSpacing;
    }

    protected abstract void pinchToZoom(float oldSpacing, float newSpacing);

    protected abstract void onTapToFocus(float x, float y);

    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }
}
