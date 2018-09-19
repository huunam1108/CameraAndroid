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

package namnh.com.cameraandroid.camera.v21;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import namnh.com.cameraandroid.camera.CameraView;
import namnh.com.cameraandroid.camera.CameraViewImpl;
import namnh.com.cameraandroid.camera.PreviewImpl;
import namnh.com.cameraandroid.camera.base.AspectRatio;
import namnh.com.cameraandroid.camera.base.Constants;
import namnh.com.cameraandroid.camera.base.Size;
import namnh.com.cameraandroid.camera.base.SizeMap;
import namnh.com.cameraandroid.camera.base.VideoQuality;

@SuppressWarnings("MissingPermission")
@TargetApi(21)
public class Camera2 extends CameraViewImpl {

    private static final String TAG = "Camera2";

    private static final SparseIntArray INTERNAL_FACINGS = new SparseIntArray();

    static {
        INTERNAL_FACINGS.put(Constants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        INTERNAL_FACINGS.put(Constants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;
    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private final CameraManager cameraManager;
    private String cameraId;
    private CameraCharacteristics cameraCharacteristics;
    private CameraDevice camera;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;
    private final SizeMap previewSizes = new SizeMap();
    private final SizeMap pictureSizes = new SizeMap();
    private Size pictureSize;
    private int facing;
    private AspectRatio aspectRatio = Constants.DEFAULT_ASPECT_RATIO;
    private AspectRatio initialRatio;
    private boolean autoFocus;
    private int flash = CameraView.Flash.FLASH_OFF;
    private int displayOrientation;
    private List<String> availableCameras = new ArrayList<>();
    private boolean isRecordingVideo;
    private File videoFile;
    private MediaRecorder mediaRecorder;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private float zoomLevel = 1f;
    private Surface previewSurface;
    private VideoQuality videoQuality = VideoQuality.DEFAULT;

    private final CameraDevice.StateCallback cameraDeviceCallback =
            new CameraDevice.StateCallback() {

                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Camera2.this.camera = camera;
                    callback.onCameraOpened();
                    startCaptureSession();
                }

                @Override
                public void onClosed(@NonNull CameraDevice camera) {
                    callback.onCameraClosed();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Camera2.this.camera = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "onError: " + camera.getId() + " (" + error + ")");
                    Camera2.this.camera = null;
                }
            };

    private final CameraCaptureSession.StateCallback sessionCallback =
            new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (!isCameraOpened()) {
                        return;
                    }
                    captureSession = session;
                    updateAutoFocus();
                    updateFlash();
                    updateZoom();
                    try {
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(),
                                captureCallback, backgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG,
                                "Failed to start camera preview because it couldn't access camera",
                                e);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Failed to start camera preview.", e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Failed to configure capture session.");
                }

                @Override
                public void onClosed(@NonNull CameraCaptureSession session) {
                    if (captureSession != null && captureSession.equals(session)) {
                        captureSession = null;
                    }
                }
            };

    private PictureCaptureCallback captureCallback = new PictureCaptureCallback() {

        @Override
        public void onPreCaptureRequired() {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            setState(STATE_PRECAPTURE);
            try {
                captureSession.capture(previewRequestBuilder.build(), this, backgroundHandler);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to run preCapture sequence.", e);
            }
        }

        @Override
        public void onReady() {
            captureStillPicture();
        }
    };

    private final ImageReader.OnImageAvailableListener onImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader reader) {
                    try (Image image = reader.acquireNextImage()) {
                        Image.Plane[] planes = image.getPlanes();
                        if (planes.length > 0) {
                            ByteBuffer buffer = planes[0].getBuffer();
                            byte[] data = new byte[buffer.remaining()];
                            buffer.get(data);
                            if (image.getFormat() == ImageFormat.JPEG) {
                                callback.onPictureTaken(data);
                            }
                            image.close();
                        }
                    } catch (Exception ignored) {
                    }
                }
            };

    public Camera2(Callback callback, PreviewImpl preview, Context context) {
        super(callback, preview);
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) return;
        cameraManager.registerAvailabilityCallback(new CameraManager.AvailabilityCallback() {
            @Override
            public void onCameraAvailable(@NonNull String cameraId) {
                super.onCameraAvailable(cameraId);
                if (availableCameras.contains(cameraId)) return;
                availableCameras.add(cameraId);
            }

            @Override
            public void onCameraUnavailable(@NonNull String cameraId) {
                super.onCameraUnavailable(cameraId);
                availableCameras.remove(cameraId);
            }
        }, backgroundHandler);

        this.preview.setCallback(new PreviewImpl.Callback() {
            @Override
            public void onSurfaceChanged() {
                startCaptureSession();
            }

            @Override
            public void onSurfaceDestroyed() {
                stop();
            }
        });
    }

    @Override
    public boolean start() {
        startBackgroundThread();
        if (!chooseCameraIdByFacing()) {
            aspectRatio = initialRatio;
            return false;
        }
        collectCameraInfo();
        setAspectRatio(initialRatio);
        initialRatio = null;
        prepareImageReader();
        startOpeningCamera();
        return true;
    }

    @Override
    public void stop() {
        resetCaptureSession();
        if (isCameraOpened()) {
            camera.close();
            camera = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        releaseRecorder();
        if (isRecordingVideo) {
            callback.onVideoRecorded(videoFile);
            isRecordingVideo = false;
        }
        // Thread must stop after everything is cleared
        stopBackgroundThread();
    }

    private void restartCamera() {
        stop();
        start();
    }

    @Override
    public boolean isCameraOpened() {
        return camera != null;
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread == null) return;
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void setFacing(int facing) {
        if (this.facing == facing) {
            return;
        }
        this.facing = facing;
        if (isCameraOpened()) {
            restartCamera();
        }
    }

    @Override
    public int getFacing() {
        return facing;
    }

    @Override
    public Set<AspectRatio> getSupportedAspectRatios() {
        return previewSizes.ratios();
    }

    @Override
    public SortedSet<Size> getAvailablePictureSizes(AspectRatio ratio) {
        return pictureSizes.sizes(ratio);
    }

    @Override
    public void setPictureSize(Size pictureSize) {
        if (pictureSize == null) return;
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
            } catch (CameraAccessException ex) {
                ex.printStackTrace();
            } finally {
                captureSession.close();
                captureSession = null;
            }
        }
        if (imageReader != null) {
            imageReader.close();
        }
        this.pictureSize = pictureSize;
        prepareImageReader();
        startCaptureSession();
    }

    @Override
    public Size getPictureSize() {
        return pictureSize;
    }

    @Override
    public boolean setAspectRatio(AspectRatio ratio) {
        if (ratio != null && previewSizes.isEmpty()) {
            initialRatio = ratio;
            return false;
        }
        if (ratio == null || ratio.equals(aspectRatio) || !previewSizes.ratios().contains(ratio)) {
            // TODO: Better error handling
            return false;
        }
        aspectRatio = ratio;
        pictureSize = pictureSizes.sizes(aspectRatio).last();
        prepareImageReader();
        // restartCamera capture session
        resetCaptureSession();
        startCaptureSession();
        return true;
    }

    @Override
    public AspectRatio getAspectRatio() {
        return aspectRatio;
    }

    @Override
    public void setAutoFocus(boolean autoFocus) {
        if (this.autoFocus == autoFocus) {
            return;
        }
        this.autoFocus = autoFocus;
        if (previewRequestBuilder == null) return;
        updateAutoFocus();
        if (captureSession != null) {
            try {
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback,
                        backgroundHandler);
            } catch (CameraAccessException e) {
                this.autoFocus = !this.autoFocus; // Revert
            }
        }
    }

    @Override
    public boolean getAutoFocus() {
        return autoFocus;
    }

    @Override
    public void setFlash(int flash) {
        if (this.flash == flash) {
            return;
        }
        int saved = this.flash;
        this.flash = flash;
        if (previewRequestBuilder == null) return;
        updateFlash();
        if (captureSession != null) {
            try {
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback,
                        backgroundHandler);
            } catch (CameraAccessException e) {
                this.flash = saved; // Revert
            }
        }
    }

    @Override
    public int getFlash() {
        return flash;
    }

    @Override
    public void setZoom(float newZoomLevel) {
        if (newZoomLevel == zoomLevel) return;
        float saved = zoomLevel;
        if (captureSession == null) return;
        updateZoom(newZoomLevel);
        try {
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler);
        } catch (CameraAccessException ex) {
            ex.printStackTrace();
            this.zoomLevel = saved; // revert
        }
    }

    @Override
    public float getZoom() {
        return zoomLevel;
    }

    @Override
    public void pinchToZoom(float oldSpacing, float newSpacing) {
        Float maxZoom =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        if (maxZoom == null
                || newSpacing > oldSpacing && zoomLevel >= maxZoom
                || newSpacing < oldSpacing && zoomLevel <= 1f) {
            // No need to update zoom level
            return;
        }
        float newZoomLevel = zoomLevel;
        float delta = 0.1f; //Control this value to control the zooming sensibility
        if (newSpacing > oldSpacing) {
            // zoom in
            if ((maxZoom - newZoomLevel) <= delta) {
                delta = maxZoom - newZoomLevel;
            }
            newZoomLevel += delta;
        } else if (newSpacing < oldSpacing) {
            // zoom out
            if ((newZoomLevel - delta) <= 1f) {
                delta = newZoomLevel - 1f;
            }
            newZoomLevel -= delta;
        }
        setZoom(newZoomLevel);
    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        this.displayOrientation = displayOrientation;
        preview.setDisplayOrientation(this.displayOrientation);
    }

    @Override
    public void setPreviewTexture(SurfaceTexture surfaceTexture) {
        if (surfaceTexture != null) {
            previewSurface = new Surface(surfaceTexture);
        } else {
            previewSurface = null;
        }
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                resetCaptureSession();
                startCaptureSession();
            }
        });
    }

    @Override
    public Size getPreviewSize() {
        return new Size(preview.getWidth(), preview.getHeight());
    }

    @Override
    public void takePicture() {
        if (autoFocus) {
            lockFocus();
        } else {
            captureStillPicture();
        }
    }

    @Override
    public void setVideoQuality(VideoQuality videoQuality) {
        if (this.videoQuality == videoQuality || isRecordingVideo) return;
        this.videoQuality = videoQuality;
    }

    @Override
    public VideoQuality getVideoQuality() {
        return videoQuality;
    }

    @Override
    public boolean startRecordingVideo(File videoFile, boolean recordAudio) {
        return startRecordingVideo(videoFile, displayOrientation, recordAudio);
    }

    @Override
    public boolean startRecordingVideo(File videoFile, int rotationAngle, boolean recordAudio) {
        if (isRecordingVideo) return false;
        setupMediaRecorder(videoFile, rotationAngle, recordAudio);
        try {
            mediaRecorder.prepare();

            // reset the capture session
            resetCaptureSession();

            Size size = chooseOptimalSize();
            preview.setBufferSize(size.getWidth(), size.getHeight());
            Surface previewSurface = getPreviewSurface();
            Surface recorderSurface = mediaRecorder.getSurface();

            previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(recorderSurface);

            camera.createCaptureSession(Arrays.asList(previewSurface, recorderSurface),
                    sessionCallback, backgroundHandler);

            mediaRecorder.start();
            isRecordingVideo = true;
            return true;
        } catch (CameraAccessException | IOException ex) {
            ex.printStackTrace();
            isRecordingVideo = false;
            restartCamera();
        }
        return false;
    }

    @Override
    public void stopRecordingVideo() {
        if (!isRecordingVideo) return;
        isRecordingVideo = false;

        if (videoFile == null || !videoFile.exists()) {
            callback.onVideoRecorded(null);
            return;
        }
        callback.onVideoRecorded(videoFile);
        videoFile = null;

        releaseRecorder();

        // restart Camera capture session
        resetCaptureSession();
        startCaptureSession();
    }

    /**
     * <p>Chooses a camera ID by the specified camera facing ({@link #facing}).</p>
     * <p>This rewrites {@link #cameraId}, {@link #cameraCharacteristics}, and optionally
     * {@link #facing}.</p>
     */
    private boolean chooseCameraIdByFacing() {
        try {
            int internalFacing = INTERNAL_FACINGS.get(facing);
            final String[] ids = cameraManager.getCameraIdList();
            if (ids.length == 0) { // No camera
                throw new RuntimeException("No camera available.");
            }
            for (String id : ids) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer level =
                        characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if (level == null
                        || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    continue;
                }
                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null) {
                    throw new NullPointerException("Unexpected state: LENS_FACING null");
                }
                if (internal == internalFacing) {
                    cameraId = id;
                    cameraCharacteristics = characteristics;
                    return true;
                }
            }
            // Not found
            cameraId = ids[0];
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            Integer level =
                    cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if (level == null
                    || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                return false;
            }
            Integer internal = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (internal == null) {
                throw new NullPointerException("Unexpected state: LENS_FACING null");
            }
            for (int i = 0, count = INTERNAL_FACINGS.size(); i < count; i++) {
                if (INTERNAL_FACINGS.valueAt(i) == internal) {
                    facing = INTERNAL_FACINGS.keyAt(i);
                    return true;
                }
            }
            // The operation can reach here when the only camera device is an external one.
            // We treat it as facing back.
            facing = Constants.FACING_BACK;
            return true;
        } catch (CameraAccessException e) {
            throw new RuntimeException("Failed to get a list of camera devices", e);
        }
    }

    /**
     * <p>Collects some information from {@link #cameraCharacteristics}.</p>
     * <p>This rewrites {@link #previewSizes}, {@link #pictureSizes}, and optionally,
     * {@link #aspectRatio}.</p>
     */
    private void collectCameraInfo() {
        StreamConfigurationMap map =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            throw new IllegalStateException("Failed to get configuration map: " + cameraId);
        }
        previewSizes.clear();
        for (android.util.Size size : map.getOutputSizes(preview.getOutputClass())) {
            int width = size.getWidth();
            int height = size.getHeight();
            if (width <= MAX_PREVIEW_WIDTH && height <= MAX_PREVIEW_HEIGHT) {
                previewSizes.add(new Size(width, height));
            }
        }
        pictureSizes.clear();
        collectPictureSizes(pictureSizes, map);
        for (AspectRatio ratio : previewSizes.ratios()) {
            if (!pictureSizes.ratios().contains(ratio)) {
                previewSizes.remove(ratio);
            }
        }

        if (!previewSizes.ratios().contains(aspectRatio)) {
            aspectRatio = previewSizes.ratios().iterator().next();
        }
    }

    protected void collectPictureSizes(SizeMap sizes, StreamConfigurationMap map) {
        for (android.util.Size size : map.getOutputSizes(ImageFormat.JPEG)) {
            pictureSizes.add(new Size(size.getWidth(), size.getHeight()));
        }
    }

    private void prepareImageReader() {
        if (pictureSizes.isEmpty() || pictureSizes.sizes(aspectRatio) == null) {
            // FIXME: 25/09/2018 Handle start, stop camera too fast
            return;
        }
        if (imageReader != null) {
            imageReader.close();
        }
        Size largest = pictureSizes.sizes(aspectRatio).last();
        imageReader =
                ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG,
                        2);
        imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
    }

    /**
     * <p>Starts opening a camera device.</p>
     * <p>The result will be processed in {@link #cameraDeviceCallback}.</p>
     */
    private void startOpeningCamera() {
        try {
            cameraManager.openCamera(cameraId, cameraDeviceCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException("Failed to open camera: " + cameraId, e);
        }
    }

    /**
     * <p>Starts a capture session for camera preview.</p>
     * <p>This rewrites {@link #previewRequestBuilder}.</p>
     * <p>The result will be continuously processed in {@link #sessionCallback}.</p>
     */
    private void startCaptureSession() {
        if (!isCameraOpened() || !preview.isReady() || imageReader == null) {
            return;
        }
        Size previewSize = chooseOptimalSize();
        preview.setBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface surface = getPreviewSurface();
        try {
            previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            camera.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    sessionCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException("Failed to start camera session");
        }
    }

    /**
     * Chooses the optimal preview size based on {@link #previewSizes} and the surface size.
     *
     * @return The picked size for camera preview.
     */
    private Size chooseOptimalSize() {
        int surfaceLonger, surfaceShorter;
        final int surfaceWidth = preview.getWidth();
        final int surfaceHeight = preview.getHeight();
        if (surfaceWidth < surfaceHeight) {
            surfaceLonger = surfaceHeight;
            surfaceShorter = surfaceWidth;
        } else {
            surfaceLonger = surfaceWidth;
            surfaceShorter = surfaceHeight;
        }
        SortedSet<Size> candidates = previewSizes.sizes(aspectRatio);

        // Pick the smallest of those big enough
        for (Size size : candidates) {
            if (size.getWidth() >= surfaceLonger && size.getHeight() >= surfaceShorter) {
                return size;
            }
        }
        // If no size is big enough, pick the largest one.
        return candidates.last();
    }

    /**
     * Updates the internal state of auto-focus to {@link #autoFocus}.
     */
    private void updateAutoFocus() {
        if (autoFocus) {
            int[] modes =
                    cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            // Auto focus is not supported
            if (modes == null || modes.length == 0 || (modes.length == 1
                    && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF);
            } else {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }
        } else {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF);
        }
    }

    /**
     * Updates the internal state of flash to {@link #flash}.
     */
    private void updateFlash() {
        switch (flash) {
            case Constants.FLASH_OFF:
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_ON:
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_TORCH:
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_TORCH);
                break;
            case Constants.FLASH_AUTO:
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_RED_EYE:
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
        }
    }

    /**
     * Locks the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            captureCallback.setState(PictureCaptureCallback.STATE_LOCKING);
            captureSession.capture(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to lock focus.", e);
        }
    }

    private void updateZoom() {
        updateZoom(1f);
    }

    private void updateZoom(float newZoomLevel) {
        Rect rect = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Float maxZoom =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        if (rect == null || maxZoom == null || newZoomLevel > maxZoom || newZoomLevel < 1f) return;
        zoomLevel = newZoomLevel;
        float ratio = 1f / newZoomLevel;
        //This ratio is the ratio of cropped Rect to Camera's original(Maximum) Rect
        //croppedWidth and croppedHeight are the pixels cropped away, not pixels after cropped
        int croppedWidth = rect.width() - Math.round((float) rect.width() * ratio);
        int croppedHeight = rect.height() - Math.round((float) rect.height() * ratio);
        //Finally, zoom represents the zoomed visible area
        Rect rectZoom =
                new Rect(croppedWidth / 2, croppedHeight / 2, rect.width() - croppedWidth / 2,
                        rect.height() - croppedHeight / 2);
        previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, rectZoom);
    }

    /**
     * Captures a still picture.
     */
    private void captureStillPicture() {
        try {
            CaptureRequest.Builder captureRequestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    previewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE));
            switch (flash) {
                case Constants.FLASH_OFF:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON);
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_OFF);
                    break;
                case Constants.FLASH_ON:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    break;
                case Constants.FLASH_TORCH:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON);
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_TORCH);
                    break;
                case Constants.FLASH_AUTO:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
                case Constants.FLASH_RED_EYE:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
            }
            // Calculate JPEG orientation.
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    getOutputRotation(displayOrientation));
            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION,
                    previewRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION));
            // Stop preview and capture a still picture.
            captureSession.stopRepeating();
            captureSession.capture(captureRequestBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            unlockFocus();
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot capture a still picture.", e);
        }
    }

    /**
     * Unlocks the auto-focus and restartCamera camera preview. This is supposed to be called after
     * capturing a still picture.
     */
    private void unlockFocus() {
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        try {
            captureSession.capture(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler);
            updateAutoFocus();
            updateFlash();
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler);
            captureCallback.setState(PictureCaptureCallback.STATE_PREVIEW);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to restartCamera camera preview.", e);
        }
    }

    private void setupMediaRecorder(File file, int rotationAngle, boolean recordAudio) {
        if (!isCameraOpened()) return;

        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
        }
        mediaRecorder.setOrientationHint(getOutputRotation(rotationAngle));

        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        if (recordAudio) {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }

        videoFile = file;
        mediaRecorder.setOutputFile(videoFile.getAbsolutePath());

        setupCamProfile(
                CamcorderProfile.get(Integer.valueOf(cameraId), CamcorderProfile.QUALITY_HIGH),
                recordAudio);

        mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
                switch (what) {
                    case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                    case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                        stopRecordingVideo();
                        break;
                }
            }
        });
        mediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mediaRecorder, int what, int extra) {
                stopRecordingVideo();
            }
        });
    }

    private void setupCamProfile(CamcorderProfile profile, boolean recordAudio) {
        // For video settings
        mediaRecorder.setOutputFormat(profile.fileFormat);
        mediaRecorder.setVideoFrameRate(profile.videoFrameRate);
        mediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mediaRecorder.setVideoEncoder(profile.videoCodec);
        if (!recordAudio) return;
        // For audio settings
        mediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
        mediaRecorder.setAudioChannels(profile.audioChannels);
        mediaRecorder.setAudioSamplingRate(profile.audioSampleRate);
        mediaRecorder.setAudioEncoder(profile.audioCodec);
    }

    private void releaseRecorder() {
        if (mediaRecorder == null) return;
        try {
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        } catch (RuntimeException ex) {
            ex.printStackTrace();
        }
    }

    private int getOutputRotation(int rotationAngle) {
        @SuppressWarnings("ConstantConditions") int sensorOrientation =
                cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        return (sensorOrientation
                + rotationAngle * (facing == Constants.FACING_FRONT ? 1 : -1)
                + 360) % 360;
    }

    private Surface getPreviewSurface() {
        if (previewSurface != null) {
            return previewSurface;
        }
        return preview.getSurface();
    }

    private void resetCaptureSession() {
        if (captureSession == null) return;
        captureSession.close();
        captureSession = null;
    }

    /**
     * A {@link CameraCaptureSession.CaptureCallback} for capturing a still picture.
     */
    private static abstract class PictureCaptureCallback
            extends CameraCaptureSession.CaptureCallback {

        static final int STATE_PREVIEW = 0;
        static final int STATE_LOCKING = 1;
        static final int STATE_LOCKED = 2;
        static final int STATE_PRECAPTURE = 3;
        static final int STATE_WAITING = 4;
        static final int STATE_CAPTURING = 5;

        private int mState;

        PictureCaptureCallback() {
        }

        void setState(int state) {
            mState = state;
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            process(result);
        }

        private void process(@NonNull CaptureResult result) {
            switch (mState) {
                case STATE_LOCKING: {
                    Integer af = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (af == null) {
                        break;
                    }
                    if (af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                            || af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            setState(STATE_CAPTURING);
                            onReady();
                        } else {
                            setState(STATE_LOCKED);
                            onPreCaptureRequired();
                        }
                    }
                    break;
                }
                case STATE_PRECAPTURE: {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null
                            || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
                            || ae == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED
                            || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        setState(STATE_WAITING);
                    }
                    break;
                }
                case STATE_WAITING: {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        setState(STATE_CAPTURING);
                        onReady();
                    }
                    break;
                }
            }
        }

        /**
         * Called when it is ready to take a still picture.
         */
        public abstract void onReady();

        /**
         * Called when it is necessary to run the pre-capture sequence.
         */
        public abstract void onPreCaptureRequired();
    }
}
