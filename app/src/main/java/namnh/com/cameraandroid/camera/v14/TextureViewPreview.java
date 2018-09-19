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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import namnh.com.cameraandroid.R;
import namnh.com.cameraandroid.camera.PreviewImpl;

@TargetApi(14)
public class TextureViewPreview extends PreviewImpl {

    private final TextureView textureView;

    private int displayOrientation;

    public TextureViewPreview(Context context, ViewGroup parent) {
        final View view = View.inflate(context, R.layout.texture_view, parent);
        textureView = view.findViewById(R.id.texture_view);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                setSize(width, height);
                configureTransform();
                dispatchSurfaceChanged();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                setSize(width, height);
                configureTransform();
                dispatchSurfaceChanged();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                setSize(0, 0);
                dispatchSurfaceDestroyed();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
    }

    // This method is called only from Camera2.
    @TargetApi(15)
    @Override
    public void setBufferSize(int width, int height) {
        textureView.getSurfaceTexture().setDefaultBufferSize(width, height);
    }

    @Override
    public Surface getSurface() {
        return new Surface(textureView.getSurfaceTexture());
    }

    @Override
    public SurfaceTexture getSurfaceTexture() {
        return textureView.getSurfaceTexture();
    }

    @Override
    public View getView() {
        return textureView;
    }

    @Override
    public Class getOutputClass() {
        return SurfaceTexture.class;
    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        this.displayOrientation = displayOrientation;
        configureTransform();
    }

    @Override
    public boolean isReady() {
        return textureView.getSurfaceTexture() != null;
    }

    /**
     * Configures the transform matrix for TextureView based on {@link #displayOrientation} and
     * the surface size.
     */
    private void configureTransform() {
        Matrix matrix = new Matrix();
        if (displayOrientation % 180 == 90) {
            final int width = getWidth();
            final int height = getHeight();
            // Rotate the camera preview when the screen is landscape.
            matrix.setPolyToPoly(new float[] {
                    0.f, 0.f, // top left
                    width, 0.f, // top right
                    0.f, height, // bottom left
                    width, height, // bottom right
            }, 0, displayOrientation == 90 ?
                    // Clockwise
                    new float[] {
                            0.f, height, // top left
                            0.f, 0.f, // top right
                            width, height, // bottom left
                            width, 0.f, // bottom right
                    } : // displayOrientation == 270
                    // Counter-clockwise
                    new float[] {
                            width, 0.f, // top left
                            width, height, // top right
                            0.f, 0.f, // bottom left
                            0.f, height, // bottom right
                    }, 0, 4);
        } else if (displayOrientation == 180) {
            matrix.postRotate(180, getWidth() / 2, getHeight() / 2);
        }
        textureView.setTransform(matrix);
    }
}
