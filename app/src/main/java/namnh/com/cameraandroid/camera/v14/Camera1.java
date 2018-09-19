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

package namnh.com.cameraandroid.camera.v14;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.support.v4.util.SparseArrayCompat;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;

import namnh.com.cameraandroid.camera.CameraView;
import namnh.com.cameraandroid.camera.CameraViewImpl;
import namnh.com.cameraandroid.camera.PreviewImpl;
import namnh.com.cameraandroid.camera.base.AspectRatio;
import namnh.com.cameraandroid.camera.base.CameraUtil;
import namnh.com.cameraandroid.camera.base.Constants;
import namnh.com.cameraandroid.camera.base.Size;
import namnh.com.cameraandroid.camera.base.SizeMap;
import namnh.com.cameraandroid.camera.base.VideoQuality;

@SuppressWarnings("deprecation")
public class Camera1 extends CameraViewImpl {

    private static final int INVALID_CAMERA_ID = -1;

    private static final SparseArrayCompat<String> FLASH_MODES = new SparseArrayCompat<>();

    static {
        FLASH_MODES.put(Constants.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF);
        FLASH_MODES.put(Constants.FLASH_ON, Camera.Parameters.FLASH_MODE_ON);
        FLASH_MODES.put(Constants.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH);
        FLASH_MODES.put(Constants.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO);
        FLASH_MODES.put(Constants.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE);
    }

    private int cameraId;
    private final AtomicBoolean isPictureCaptureInProgress = new AtomicBoolean(false);
    private Camera camera;
    private Camera.Parameters cameraParameters;
    private final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
    private final SizeMap previewSizes = new SizeMap();
    private final SizeMap pictureSizes = new SizeMap();
    private Size pictureSize;
    private AspectRatio aspectRatio = Constants.DEFAULT_ASPECT_RATIO;
    private boolean showingPreview;
    private boolean autoFocus;
    private int facing = CameraView.Facing.FACING_BACK;
    private int flash = CameraView.Flash.FLASH_OFF;
    private int displayOrientation;
    private float zoomLevel;
    private SurfaceTexture previewTexture;
    private MediaRecorder mediaRecorder;
    private File videoFile;
    private boolean isRecordingVideo;
    private VideoQuality videoQuality = VideoQuality.DEFAULT;

    public Camera1(CameraViewImpl.Callback callback, PreviewImpl preview) {
        super(callback, preview);
        preview.setCallback(new PreviewImpl.Callback() {
            @Override
            public void onSurfaceChanged() {
                if (camera != null) {
                    setUpPreview();
                    adjustCameraParameters();
                }
            }

            @Override
            public void onSurfaceDestroyed() {
                stop();
            }
        });
    }

    @Override
    public boolean start() {
        chooseCamera();
        if (!openCamera()) return true;// returning false will result in invoking this method again
        if (preview.isReady()) {
            setUpPreview();
        }
        showingPreview = true;
        startCameraPreview();
        return true;
    }

    @Override
    public void stop() {
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
        }
        showingPreview = false;
        releaseRecorder();
        if (isRecordingVideo) {
            callback.onVideoRecorded(videoFile);
            isRecordingVideo = false;
        }
        releaseCamera();
    }

    private void restart() {
        stop();
        start();
    }

    @SuppressLint("NewApi")
    private void setUpPreview() {
        try {
            if (previewTexture != null) {
                camera.setPreviewTexture(previewTexture);
            } else if (preview.getOutputClass() == SurfaceHolder.class) {
                camera.setPreviewDisplay(preview.getSurfaceHolder());
            } else {
                camera.setPreviewTexture((SurfaceTexture) preview.getSurfaceTexture());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void startCameraPreview() {
        // TODO: 21/09/2018 Update this method, pause or resume preview
        camera.startPreview();
    }

    @Override
    public boolean isCameraOpened() {
        return camera != null;
    }

    @Override
    public void setFacing(int facing) {
        if (this.facing == facing) {
            return;
        }
        this.facing = facing;
        if (isCameraOpened()) {
            restart();
        }
    }

    @Override
    public int getFacing() {
        return facing;
    }

    @Override
    public Set<AspectRatio> getSupportedAspectRatios() {
        SizeMap idealAspectRatios = previewSizes;
        for (AspectRatio aspectRatio : idealAspectRatios.ratios()) {
            if (pictureSizes.sizes(aspectRatio) == null) {
                idealAspectRatios.remove(aspectRatio);
            }
        }
        return idealAspectRatios.ratios();
    }

    @Override
    public SortedSet<Size> getAvailablePictureSizes(AspectRatio ratio) {
        return pictureSizes.sizes(ratio);
    }

    @Override
    public void setPictureSize(Size pictureSize) {
        if (pictureSize == null) return;
        this.pictureSize = pictureSize;
        if (cameraParameters != null && camera != null) {
            cameraParameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
            camera.setParameters(cameraParameters);
        }
    }

    @Override
    public Size getPictureSize() {
        return pictureSize;
    }

    @Override
    public boolean setAspectRatio(AspectRatio ratio) {
        if (aspectRatio == null || !isCameraOpened()) {
            // Handle this later when camera is opened
            aspectRatio = ratio;
            return true;
        } else if (!aspectRatio.equals(ratio)) {
            final Set<Size> sizes = previewSizes.sizes(ratio);
            if (sizes == null) {
                throw new UnsupportedOperationException(ratio + " is not supported");
            } else {
                aspectRatio = ratio;
                pictureSize = pictureSizes.sizes(ratio).last(); // reset picture size
                adjustCameraParameters();
                return true;
            }
        }
        return false;
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
        if (setAutoFocusInternal(autoFocus)) {
            camera.setParameters(cameraParameters);
        }
    }

    @Override
    public boolean getAutoFocus() {
        if (!isCameraOpened()) {
            return autoFocus;
        }
        String focusMode = cameraParameters.getFocusMode();
        return focusMode != null && focusMode.contains("continuous");
    }

    @Override
    public void setFlash(int flash) {
        if (flash == this.flash) {
            return;
        }
        if (setFlashInternal(flash)) {
            camera.setParameters(cameraParameters);
        }
    }

    @Override
    public int getFlash() {
        return flash;
    }

    @Override
    public void setZoom(float newZoomLevel) {
        if (newZoomLevel == zoomLevel) return;
        if (setZoomInternal(newZoomLevel)) {
            camera.setParameters(cameraParameters);
        }
    }

    @Override
    public float getZoom() {
        return zoomLevel;
    }

    @Override
    public void pinchToZoom(float oldSpacing, float newSpacing) {
        if (cameraParameters != null && cameraParameters.isZoomSupported()) {
            int maxZoom = cameraParameters.getMaxZoom();
            if (newSpacing > oldSpacing && zoomLevel >= maxZoom
                    || newSpacing < oldSpacing && zoomLevel <= 1f) {
                // No need to update zoom level
                return;
            }
            float newZoomLevel = zoomLevel;
            if (newSpacing > oldSpacing) {
                newZoomLevel += 1f;
            } else if (newSpacing < oldSpacing) {
                newZoomLevel -= 1f;
            }
            setZoom(newZoomLevel);
        }
    }

    @Override
    public void takePicture() {
        if (!isCameraOpened()) {
            throw new IllegalStateException(
                    "Camera is not ready. Call start() before takePicture().");
        }
        if (getAutoFocus()) {
            camera.cancelAutoFocus();
            camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    takePictureInternal();
                }
            });
        } else {
            takePictureInternal();
        }
    }

    private void takePictureInternal() {
        if (!isPictureCaptureInProgress.getAndSet(true)) {
            camera.takePicture(null, null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    isPictureCaptureInProgress.set(false);
                    camera.cancelAutoFocus();
                    camera.startPreview();
                    callback.onPictureTaken(data);
                }
            });
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
            mediaRecorder.start();
            isRecordingVideo = true;
            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
            isRecordingVideo = false;
            restart();
        }
        return false;
    }

    @Override
    public void stopRecordingVideo() {
        if (!isRecordingVideo) return;
        isRecordingVideo = false;
        if (camera != null) camera.lock();

        releaseRecorder();
        // Save the current video
        if (videoFile == null || !videoFile.exists()) {
            callback.onVideoRecorded(null);
            return;
        }

        callback.onVideoRecorded(videoFile);
        videoFile = null;
    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        if (this.displayOrientation == displayOrientation) {
            return;
        }
        this.displayOrientation = displayOrientation;
        if (isCameraOpened()) {
            cameraParameters.setRotation(calcCameraRotation(displayOrientation));
            camera.setParameters(cameraParameters);
            camera.setDisplayOrientation(calcDisplayOrientation(displayOrientation));
        }
    }

    @Override
    public void setPreviewTexture(SurfaceTexture previewTexture) {
        if (camera == null) {
            this.previewTexture = previewTexture;
            return;
        }
        camera.stopPreview();
        try {
            if (previewTexture == null) {
                camera.setPreviewTexture((SurfaceTexture) preview.getSurfaceTexture());
            } else {
                camera.setPreviewTexture(previewTexture);
            }
            this.previewTexture = previewTexture;
            startCameraPreview();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public Size getPreviewSize() {
        Camera.Size cameraSize = cameraParameters.getPreviewSize();
        return new Size(cameraSize.width, cameraSize.height);
    }

    /**
     * This rewrites {@link #cameraId} and {@link #cameraInfo}.
     */
    private void chooseCamera() {
        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == facing) {
                cameraId = i;
                return;
            }
        }
        cameraId = INVALID_CAMERA_ID;
    }

    private boolean openCamera() {
        if (camera != null) {
            releaseCamera();
        }
        try {
            camera = Camera.open(cameraId);
            cameraParameters = camera.getParameters();
            // Supported preview sizes
            previewSizes.clear();
            for (Camera.Size size : cameraParameters.getSupportedPreviewSizes()) {
                previewSizes.add(new Size(size.width, size.height));
            }
            // Supported picture sizes;
            pictureSizes.clear();
            for (Camera.Size size : cameraParameters.getSupportedPictureSizes()) {
                pictureSizes.add(new Size(size.width, size.height));
            }
            // AspectRatio
            if (aspectRatio == null) {
                aspectRatio = Constants.DEFAULT_ASPECT_RATIO;
            }
            adjustCameraParameters();
            camera.setDisplayOrientation(calcDisplayOrientation(displayOrientation));
            callback.onCameraOpened();
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private AspectRatio chooseAspectRatio() {
        AspectRatio r = null;
        for (AspectRatio ratio : previewSizes.ratios()) {
            r = ratio;
            if (ratio.equals(Constants.DEFAULT_ASPECT_RATIO)) {
                return ratio;
            }
        }
        return r;
    }

    private void adjustCameraParameters() {
        SortedSet<Size> sizes = previewSizes.sizes(aspectRatio);
        if (sizes == null) { // Not supported
            aspectRatio = chooseAspectRatio();
            sizes = previewSizes.sizes(aspectRatio);
        }
        Size size = chooseOptimalSize(sizes);

        // Always re-apply camera parameters
        // Largest picture size in this ratio
        if (pictureSize == null) {
            pictureSize = pictureSizes.sizes(aspectRatio).last();
        }
        if (showingPreview) {
            camera.stopPreview();
        }
        cameraParameters.setPreviewSize(size.getWidth(), size.getHeight());
        cameraParameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
        cameraParameters.setRotation(calcCameraRotation(displayOrientation));
        // Update latest camera settings
        setAutoFocusInternal(autoFocus);
        setFlashInternal(flash);
        setAspectRatio(aspectRatio);
        setZoomInternal(zoomLevel);
        // apply all settings
        camera.setParameters(cameraParameters);
        if (showingPreview) {
            camera.startPreview();
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private Size chooseOptimalSize(SortedSet<Size> sizes) {
        if (!preview.isReady()) { // Not yet laid out
            return sizes.first(); // Return the smallest size
        }
        int desiredWidth;
        int desiredHeight;
        final int surfaceWidth = preview.getWidth();
        final int surfaceHeight = preview.getHeight();
        if (isLandscape(displayOrientation)) {
            desiredWidth = surfaceHeight;
            desiredHeight = surfaceWidth;
        } else {
            desiredWidth = surfaceWidth;
            desiredHeight = surfaceHeight;
        }
        Size result = null;
        for (Size size : sizes) { // Iterate from small to large
            if (desiredWidth <= size.getWidth() && desiredHeight <= size.getHeight()) {
                return size;
            }
            result = size;
        }
        return result;
    }

    private void releaseCamera() {
        if (!isCameraOpened()) return;
        camera.release();
        camera = null;
        pictureSize = null;
        callback.onCameraClosed();
    }

    /**
     * Calculate display orientation
     * https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
     * <p>
     * This calculation is used for orienting the preview
     * <p>
     * Note: This is not the same calculation as the camera rotation
     *
     * @param screenOrientationDegrees Screen orientation in degrees
     * @return Number of degrees required to rotate preview
     */
    private int calcDisplayOrientation(int screenOrientationDegrees) {
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - (cameraInfo.orientation + screenOrientationDegrees) % 360) % 360;
        } else {  // back-facing
            return (cameraInfo.orientation - screenOrientationDegrees + 360) % 360;
        }
    }

    /**
     * Calculate camera rotation
     * <p>
     * This calculation is applied to the output JPEG either via Exif Orientation tag
     * or by actually transforming the bitmap. (Determined by vendor camera API implementation)
     * <p>
     * Note: This is not the same calculation as the display orientation
     *
     * @param screenOrientationDegrees Screen orientation in degrees
     * @return Number of degrees to rotate image in order for it to view correctly.
     */
    private int calcCameraRotation(int screenOrientationDegrees) {
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (cameraInfo.orientation + screenOrientationDegrees) % 360;
        } else {  // back-facing
            final int landscapeFlip = isLandscape(screenOrientationDegrees) ? 180 : 0;
            return (cameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360;
        }
    }

    /**
     * Test if the supplied orientation is in landscape.
     *
     * @param orientationDegrees Orientation in degrees (0,90,180,270)
     * @return True if in landscape, false if portrait
     */
    private boolean isLandscape(int orientationDegrees) {
        return (orientationDegrees == Constants.LANDSCAPE_90
                || orientationDegrees == Constants.LANDSCAPE_270);
    }

    /**
     * @return {@code true} if {@link #cameraParameters} was modified.
     */
    private boolean setAutoFocusInternal(boolean autoFocus) {
        this.autoFocus = autoFocus;
        if (isCameraOpened()) {
            final List<String> modes = cameraParameters.getSupportedFocusModes();
            if (autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            } else {
                cameraParameters.setFocusMode(modes.get(0));
            }
            return true;
        }
        return false;
    }

    /**
     * @return {@code true} if {@link #cameraParameters} was modified.
     */
    private boolean setFlashInternal(int flash) {
        if (isCameraOpened()) {
            List<String> modes = cameraParameters.getSupportedFlashModes();
            String mode = FLASH_MODES.get(flash);
            if (modes != null && modes.contains(mode)) {
                cameraParameters.setFlashMode(mode);
                this.flash = flash;
                return true;
            }
            String currentMode = FLASH_MODES.get(this.flash);
            if (modes == null || !modes.contains(currentMode)) {
                cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                this.flash = Constants.FLASH_OFF;
                return true;
            }
            return false;
        } else {
            this.flash = flash;
            return false;
        }
    }

    private boolean setZoomInternal(float newZoomLevel) {
        if (isCameraOpened() && cameraParameters != null && cameraParameters.isZoomSupported()) {
            int maxZoom = cameraParameters.getMaxZoom();
            newZoomLevel = newZoomLevel < 1 ? 1 : newZoomLevel;
            newZoomLevel = newZoomLevel > maxZoom ? maxZoom : newZoomLevel;
            zoomLevel = newZoomLevel;
            cameraParameters.setZoom((int) newZoomLevel);
            return true;
        }
        // TODO: 26/09/2018 Notify the invalid zoomLevel here
        return false;
    }

    private void setupMediaRecorder(File videoFile, int rotationAngle, boolean recordAudio) {
        if (camera == null) return;
        this.videoFile = videoFile;
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
        }
        mediaRecorder.setOrientationHint(calcCameraRotation(rotationAngle)); // must be set before `mediaRecorder.setCamera(camera)`
        camera.unlock();
        mediaRecorder.setCamera(camera);

        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        if (recordAudio) {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        }

        mediaRecorder.setOutputFile(videoFile.getAbsolutePath());

        setupCamProfile(CameraUtil.getCamcorderProfile(videoQuality, cameraId), recordAudio);
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
                // TODO: 25/09/2018 Notify error
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
}
