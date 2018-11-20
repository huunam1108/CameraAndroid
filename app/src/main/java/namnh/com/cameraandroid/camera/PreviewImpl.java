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

import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;

/**
 * Encapsulates all the operations related to camera preview in a backward-compatible manner.
 */
public abstract class PreviewImpl {

    public interface Callback {
        void onSurfaceChanged();

        void onSurfaceDestroyed();
    }

    private Callback callback;

    private int width;

    private int height;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public abstract Surface getSurface();

    public abstract View getView();

    public abstract Class getOutputClass();

    public abstract void setDisplayOrientation(int displayOrientation);

    public abstract boolean isReady();

    protected void dispatchSurfaceChanged() {
        callback.onSurfaceChanged();
    }

    protected void dispatchSurfaceDestroyed() {
        callback.onSurfaceDestroyed();
    }

    public SurfaceHolder getSurfaceHolder() {
        return null;
    }

    public Object getSurfaceTexture() {
        return null;
    }

    public void setBufferSize(int width, int height) {
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
