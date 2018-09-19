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

import android.graphics.SurfaceTexture;
import android.view.View;

import java.io.File;
import java.util.Set;
import java.util.SortedSet;

import namnh.com.cameraandroid.camera.base.AspectRatio;
import namnh.com.cameraandroid.camera.base.Size;
import namnh.com.cameraandroid.camera.base.VideoQuality;

public abstract class CameraViewImpl {

    protected final Callback callback;

    protected final PreviewImpl preview;

    public CameraViewImpl(Callback callback, PreviewImpl preview) {
        this.callback = callback;
        this.preview = preview;
    }

    public View getView() {
        return preview.getView();
    }

    public PreviewImpl getPreview() {
        return preview;
    }

    /**
     * @return {@code true} if the implementation was able to start the camera session.
     */
    public abstract boolean start();

    public abstract void stop();

    public abstract boolean isCameraOpened();

    // For facing camera setup
    public abstract void setFacing(int facing);

    public abstract int getFacing();

    public abstract Set<AspectRatio> getSupportedAspectRatios();

    public abstract SortedSet<Size> getAvailablePictureSizes(AspectRatio ratio);

    public abstract Size getPictureSize();

    public abstract void setPictureSize(Size pictureSize);

    /**
     * @return {@code true} if the aspect ratio was changed.
     */
    public abstract boolean setAspectRatio(AspectRatio ratio);

    public abstract AspectRatio getAspectRatio();

    public abstract void setAutoFocus(boolean autoFocus);

    public abstract boolean getAutoFocus();

    public abstract void setFlash(int flash);

    public abstract int getFlash();

    public abstract void pinchToZoom(float oldSpacing, float newSpacing);

    public abstract void setZoom(float zoomLevel);

    public abstract float getZoom();

    public abstract void takePicture();

    public abstract void setVideoQuality(VideoQuality videoQuality);

    public abstract VideoQuality getVideoQuality();

    public abstract boolean startRecordingVideo(File videoFile, int rotationAngle, boolean recordAudio);

    public abstract boolean startRecordingVideo(File videoFile, boolean recordAudio);

    public abstract void stopRecordingVideo();

    public abstract void setDisplayOrientation(int displayOrientation);

    public abstract void setPreviewTexture(SurfaceTexture surfaceTexture);

    public abstract Size getPreviewSize();

    public interface Callback {

        void onCameraOpened();

        void onCameraClosed();

        void onPictureTaken(byte[] data);

        void onVideoRecorded(File videoFile);
    }
}
