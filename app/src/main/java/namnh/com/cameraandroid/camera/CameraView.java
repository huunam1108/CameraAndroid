/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package namnh.com.cameraandroid.camera;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Set;
import namnh.com.cameraandroid.R;
import namnh.com.cameraandroid.camera.base.AspectRatio;
import namnh.com.cameraandroid.camera.base.Constants;
import namnh.com.cameraandroid.camera.base.DisplayOrientationDetector;
import namnh.com.cameraandroid.camera.base.Size;
import namnh.com.cameraandroid.camera.base.VideoQuality;
import namnh.com.cameraandroid.camera.v14.Camera1;
import namnh.com.cameraandroid.camera.v14.TextureViewPreview;
import namnh.com.cameraandroid.camera.v21.Camera2;
import namnh.com.cameraandroid.camera.v23.Camera2Api23;

public class CameraView extends CameraViewLayout {
    private static final String SONY_DEVICE = "Sony";

    /** Direction the camera faces relative to device screen. */
    @IntDef({ Facing.FACING_BACK, Facing.FACING_FRONT })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Facing {
        /** The camera device faces the opposite direction as the device's screen. */
        int FACING_BACK = Constants.FACING_BACK;

        /** The camera device faces the same direction as the device's screen. */
        int FACING_FRONT = Constants.FACING_FRONT;
    }

    /** The mode for for the camera device's flash control */
    @IntDef({
            Flash.FLASH_OFF, Flash.FLASH_ON, Flash.FLASH_TORCH, Flash.FLASH_AUTO,
            Flash.FLASH_RED_EYE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flash {
        /** Flash will not be fired. */
        int FLASH_OFF = Constants.FLASH_OFF;

        /** Flash will always be fired during snapshot. */
        int FLASH_ON = Constants.FLASH_ON;

        /** Constant emission of light during preview, auto-focus and snapshot. */
        int FLASH_TORCH = Constants.FLASH_TORCH;

        /** Flash will be fired automatically when required. */
        int FLASH_AUTO = Constants.FLASH_AUTO;

        /** Flash will be fired in red-eye reduction mode. */
        int FLASH_RED_EYE = Constants.FLASH_RED_EYE;
    }

    private CameraViewImpl cameraViewImpl;

    private final CallbackBridge callbacks;

    private boolean adjustViewBounds;

    private boolean isStarted;

    private final DisplayOrientationDetector displayOrientationDetector;

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressWarnings("WrongConstant")
    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (isInEditMode()) {
            callbacks = null;
            displayOrientationDetector = null;
            return;
        }

        // Internal setup
        final PreviewImpl preview = createPreviewImpl(context);
        callbacks = new CallbackBridge(this);

        // So, we have an issue when using Camera2 with Sony's device
        // ref: https://github.com/google/cameraview/issues/184
        // we will use Camera1 for Sony's device
        if (Build.VERSION.SDK_INT < 21 || SONY_DEVICE.equals(Build.MANUFACTURER) || isEmulator()) {
            cameraViewImpl = new Camera1(callbacks, preview);
        } else if (Build.VERSION.SDK_INT < 23) {
            cameraViewImpl = new Camera2(callbacks, preview, context);
        } else {
            cameraViewImpl = new Camera2Api23(callbacks, preview, context);
        }

        isStarted = false;
        // Attributes
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraView, defStyleAttr,
                R.style.Widget_CameraView);
        adjustViewBounds = a.getBoolean(R.styleable.CameraView_android_adjustViewBounds, false);
        setFacing(a.getInt(R.styleable.CameraView_facing, Facing.FACING_BACK));
        String aspectRatio = a.getString(R.styleable.CameraView_aspectRatio);
        if (aspectRatio != null) {
            setAspectRatio(AspectRatio.parse(aspectRatio));
        } else {
            setAspectRatio(Constants.DEFAULT_ASPECT_RATIO);
        }
        setAutoFocus(a.getBoolean(R.styleable.CameraView_autoFocus, true));
        setFlash(a.getInt(R.styleable.CameraView_flash, Constants.FLASH_OFF));
        setZoom(a.getFloat(R.styleable.CameraView_zoom, Constants.DEFAULT_ZOOM));
        a.recycle();
        // Display orientation detector
        displayOrientationDetector = new DisplayOrientationDetector(context) {
            @Override
            public void onDisplayOrientationChanged(int displayOrientation) {
                cameraViewImpl.setDisplayOrientation(displayOrientation);
            }
        };
    }

    private boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    @NonNull
    private PreviewImpl createPreviewImpl(Context context) {
        return new TextureViewPreview(context, this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            displayOrientationDetector.enable(ViewCompat.getDisplay(this));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!isInEditMode()) {
            displayOrientationDetector.disable();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isInEditMode()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        // Handle android:adjustViewBounds
        if (adjustViewBounds) {
            if (!isCameraOpened()) {
                callbacks.reserveRequestLayoutOnOpen();
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                final AspectRatio ratio = getAspectRatio();
                assert ratio != null;
                int height = (int) (MeasureSpec.getSize(widthMeasureSpec) * ratio.toFloat());
                if (heightMode == MeasureSpec.AT_MOST) {
                    height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
                }
                super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            } else if (widthMode != MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
                final AspectRatio ratio = getAspectRatio();
                assert ratio != null;
                int width = (int) (MeasureSpec.getSize(heightMeasureSpec) * ratio.toFloat());
                if (widthMode == MeasureSpec.AT_MOST) {
                    width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        heightMeasureSpec);
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        // Measure the TextureView
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        AspectRatio ratio = getAspectRatio();
        if (displayOrientationDetector.getLastKnownDisplayOrientation() % 180 == 0) {
            ratio = ratio.inverse();
        }
        assert ratio != null;
        if (height < width * ratio.getY() / ratio.getX()) {
            cameraViewImpl.getView()
                    .measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(width * ratio.getY() / ratio.getX(),
                                    MeasureSpec.EXACTLY));
        } else {
            cameraViewImpl.getView()
                    .measure(MeasureSpec.makeMeasureSpec(height * ratio.getX() / ratio.getY(),
                            MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.facing = getFacing();
        state.ratio = getAspectRatio();
        state.autoFocus = getAutoFocus();
        state.flash = getFlash();
        state.zoom = getZoom();
        state.pictureSize = getPictureSize();
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setFacing(ss.facing);
        setAspectRatio(ss.ratio);
        setAutoFocus(ss.autoFocus);
        setFlash(ss.flash);
        setZoom(ss.zoom);
        setPictureSize(ss.pictureSize);
    }

    /**
     * Open a camera device and start showing camera preview. This is typically called from
     * {@link Activity#onResume()}.
     */
    public void start() {
        if (isStarted || !isEnabled()) return;
        isStarted = true;

        if (!cameraViewImpl.start()) {
            if (cameraViewImpl.getView() != null) {
                removeView(cameraViewImpl.getView());
            }
            //store the state ,and restore this state after fall back o Camera1
            Parcelable state = onSaveInstanceState();
            // Camera2 uses legacy hardware layer; fall back to Camera1
            cameraViewImpl = new Camera1(callbacks, createPreviewImpl(getContext()));
            onRestoreInstanceState(state);
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    cameraViewImpl.start();
                }
            }, 100);
        }
    }

    /**
     * Stop camera preview and close the device. This is typically called from
     * {@link Activity#onPause()}.
     */
    public void stop() {
        if (!isStarted) return;
        isStarted = false;
        cameraViewImpl.stop();
    }

    /**
     * @return {@code true} if the camera is opened.
     */
    public boolean isCameraOpened() {
        return cameraViewImpl.isCameraOpened();
    }

    /**
     * Add a new callbacks.
     *
     * @param callback The {@link Callback} to add.
     * @see #removeCallback(Callback)
     */
    public void addCallback(@NonNull Callback callback) {
        callbacks.add(callback);
    }

    /**
     * Remove a callbacks.
     *
     * @param callback The {@link Callback} to remove.
     * @see #addCallback(Callback)
     */
    public boolean removeCallback(@NonNull Callback callback) {
        return callbacks.remove(callback);
    }

    /**
     * @param adjustViewBounds {@code true} if you want the CameraView to adjust its bounds to
     * preserve the aspect ratio of camera.
     * @see #getAdjustViewBounds()
     */
    public void setAdjustViewBounds(boolean adjustViewBounds) {
        if (this.adjustViewBounds != adjustViewBounds) {
            this.adjustViewBounds = adjustViewBounds;
            requestLayout();
        }
    }

    /**
     * @return True when this CameraView is adjusting its bounds to preserve the aspect ratio of
     * camera.
     * @see #setAdjustViewBounds(boolean)
     */
    public boolean getAdjustViewBounds() {
        return adjustViewBounds;
    }

    /**
     * Chooses camera by the direction it faces.
     *
     * @param facing The camera facing. Must be either {@link Facing#FACING_BACK} or
     * {@link Facing#FACING_FRONT}.
     */
    public void setFacing(@Facing final int facing) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                cameraViewImpl.setFacing(facing);
            }
        });
    }

    /**
     * Gets the direction that the current camera faces.
     *
     * @return The camera facing.
     */
    @Facing
    public int getFacing() {
        //noinspection WrongConstant
        return cameraViewImpl.getFacing();
    }

    /**
     * Gets all the aspect ratios supported by the current camera.
     */
    public Set<AspectRatio> getSupportedAspectRatios() {
        return cameraViewImpl.getSupportedAspectRatios();
    }

    /**
     * Sets the aspect ratio of camera.
     *
     * @param ratio The {@link AspectRatio} to be set.
     */
    public void setAspectRatio(@NonNull AspectRatio ratio) {
        if (cameraViewImpl.setAspectRatio(ratio)) {
            requestLayout();
        }
    }

    /**
     * Gets the current aspect ratio of camera.
     *
     * @return The current {@link AspectRatio}. Can be {@code null} if no camera is opened yet.
     */
    @Nullable
    public AspectRatio getAspectRatio() {
        return cameraViewImpl.getAspectRatio();
    }

    /**
     * Enables or disables the continuous auto-focus mode. When the current camera doesn't support
     * auto-focus, calling this method will be ignored.
     *
     * @param autoFocus {@code true} to enable continuous auto-focus mode. {@code false} to
     * disable it.
     */
    public void setAutoFocus(boolean autoFocus) {
        cameraViewImpl.setAutoFocus(autoFocus);
    }

    /**
     * Returns whether the continuous auto-focus mode is enabled.
     *
     * @return {@code true} if the continuous auto-focus mode is enabled. {@code false} if it is
     * disabled, or if it is not supported by the current camera.
     */
    public boolean getAutoFocus() {
        return cameraViewImpl.getAutoFocus();
    }

    /**
     * Sets the flash mode.
     *
     * @param flash The desired flash mode.
     */
    public void setFlash(@Flash int flash) {
        cameraViewImpl.setFlash(flash);
    }

    /**
     * Gets the current flash mode.
     *
     * @return The current flash mode.
     */
    @Flash
    public int getFlash() {
        //noinspection WrongConstant
        return cameraViewImpl.getFlash();
    }

    /**
     * Take a picture. The result will be returned to
     * {@link Callback#onPictureTaken(CameraView, byte[])}.
     */
    public void takePicture() {
        cameraViewImpl.takePicture();
    }

    /**
     * Record video with manually settings a rotation angle
     *
     * @param file the recorded video file
     * @param rotationAngle the rotation angle of video output
     * @param recordAudio true if enable the video audio, otherwise false
     * @return true if video recording is started successful
     */
    public boolean starRecordingVideo(File file, int rotationAngle, boolean recordAudio) {
        return cameraViewImpl.startRecordingVideo(file, rotationAngle, recordAudio);
    }

    /**
     * Record video with auto detect rotation. This is useful for case activity unset fixed
     * configuration change (portrait, landscape...)
     *
     * @param file the video files
     * @param recordAudio true if enable the video audio, otherwise false
     * @return true if video recording is started successful
     */
    public boolean starRecordingVideo(File file, boolean recordAudio) {
        return cameraViewImpl.startRecordingVideo(file, recordAudio);
    }

    public void stopRecordingVideo() {
        cameraViewImpl.stopRecordingVideo();
    }

    /**
     * Sets the size of taken pictures.
     *
     * @param size The {@link Size} to be set.
     */
    public void setPictureSize(@NonNull Size size) {
        cameraViewImpl.setPictureSize(size);
    }

    /**
     * Gets the size of pictures that will be taken.
     */
    public Size getPictureSize() {
        return cameraViewImpl.getPictureSize();
    }

    @Override
    protected void onTapToFocus(float x, float y) {
        // TODO: 21/09/2018 Focus implementation

    }

    @Override
    protected void pinchToZoom(float oldSpacing, float newSpacing) {
        cameraViewImpl.pinchToZoom(oldSpacing, newSpacing);
    }

    public void setZoom(float zoomLevel) {
        cameraViewImpl.setZoom(zoomLevel);
    }

    public float getZoom() {
        return cameraViewImpl.getZoom();
    }

    public void setVideoQuality(@NonNull VideoQuality videoQuality) {
        cameraViewImpl.setVideoQuality(videoQuality);
    }

    public VideoQuality getVideoQuality() {
        return cameraViewImpl.getVideoQuality();
    }

    private static class CallbackBridge implements CameraViewImpl.Callback {

        private final ArrayList<Callback> callbacks = new ArrayList<>();
        private final WeakReference<CameraView> cameraView;

        private boolean mRequestLayoutOnOpen;

        CallbackBridge(CameraView cameraView) {
            this.cameraView = new WeakReference<>(cameraView);
        }

        public void add(Callback callback) {
            if (callbacks.contains(callback)) return;
            this.callbacks.add(callback);
        }

        public boolean remove(Callback callback) {
            return callbacks.remove(callback);
        }

        @Override
        public void onCameraOpened() {
            if (mRequestLayoutOnOpen) {
                mRequestLayoutOnOpen = false;
                if (cameraView.get() != null) {
                    cameraView.get().requestLayout();
                }
            }
            for (Callback callback : callbacks) {
                callback.onCameraOpened(cameraView.get());
            }
        }

        @Override
        public void onCameraClosed() {
            for (Callback callback : callbacks) {
                callback.onCameraClosed(cameraView.get());
            }
        }

        @Override
        public void onPictureTaken(byte[] data) {
            for (Callback callback : callbacks) {
                callback.onPictureTaken(cameraView.get(), data);
            }
        }

        @Override
        public void onVideoRecorded(File videoFile) {
            for (Callback callback : callbacks) {
                callback.onVideoRecorded(cameraView.get(), videoFile);
            }
        }

        public void reserveRequestLayoutOnOpen() {
            mRequestLayoutOnOpen = true;
        }
    }

    protected static class SavedState extends BaseSavedState {

        @Facing
        int facing;

        AspectRatio ratio;

        boolean autoFocus;

        @Flash
        int flash;

        float zoom;

        Size pictureSize;

        @SuppressWarnings("WrongConstant")
        public SavedState(Parcel source, ClassLoader loader) {
            super(source);
            facing = source.readInt();
            ratio = source.readParcelable(loader);
            autoFocus = source.readByte() != 0;
            flash = source.readInt();
            zoom = source.readFloat();
            pictureSize = source.readParcelable(loader);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(facing);
            out.writeParcelable(ratio, 0);
            out.writeByte((byte) (autoFocus ? 1 : 0));
            out.writeInt(flash);
            out.writeFloat(zoom);
            out.writeParcelable(pictureSize, flags);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.ClassLoaderCreator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel parcel) {
                        return null;
                    }

                    @Override
                    public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                        return new SavedState(in, loader);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    /**
     * Callback for monitoring events about {@link CameraView}.
     */
    @SuppressWarnings("UnusedParameters")
    public abstract static class Callback {

        /**
         * Called when camera is opened.
         *
         * @param cameraView The associated {@link CameraView}.
         */
        public void onCameraOpened(CameraView cameraView) {
        }

        /**
         * Called when camera is closed.
         *
         * @param cameraView The associated {@link CameraView}.
         */
        public void onCameraClosed(CameraView cameraView) {
        }

        /**
         * Called when a picture is taken.
         *
         * @param cameraView The associated {@link CameraView}.
         * @param data JPEG data.
         */
        public void onPictureTaken(CameraView cameraView, byte[] data) {
        }

        /**
         * Called when a video is recorded.
         *
         * @param cameraView The associated {@link CameraView}.
         * @param videoFile The recorded video
         */
        public void onVideoRecorded(CameraView cameraView, File videoFile) {
        }
    }
}
