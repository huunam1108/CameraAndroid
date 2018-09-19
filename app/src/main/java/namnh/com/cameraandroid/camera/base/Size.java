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

package namnh.com.cameraandroid.camera.base;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/**
 * Immutable class for describing width and height dimensions in pixels.
 */
public class Size implements Comparable<Size>, Parcelable {

    private final int width;
    private final int height;

    /**
     * Create a new immutable Size instance.
     *
     * @param width The width of the size, in pixels
     * @param height The height of the size, in pixels
     */
    public Size(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (this == other) {
            return true;
        }
        if (other instanceof Size) {
            Size size = (Size) other;
            return width == size.width && height == size.height;
        }
        return false;
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }

    @Override
    public int hashCode() {
        // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
        return height ^ ((width << (Integer.SIZE / 2)) | (width >>> (Integer.SIZE / 2)));
    }

    @Override
    public int compareTo(@NonNull Size other) {
        return width * height - other.width * other.height;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(width);
        dest.writeInt(height);
    }

    public static final Creator<Size> CREATOR = new Creator<Size>() {

        @Override
        public Size createFromParcel(Parcel source) {
            int width = source.readInt();
            int height = source.readInt();
            return new Size(width, height);
        }

        @Override
        public Size[] newArray(int size) {
            return new Size[size];
        }
    };
}
