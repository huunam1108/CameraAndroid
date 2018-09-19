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
import android.support.v4.util.SparseArrayCompat;

/**
 * Immutable class for describing proportional relationship between width and height.
 */
public class AspectRatio implements Comparable<AspectRatio>, Parcelable {

    private final static SparseArrayCompat<SparseArrayCompat<AspectRatio>> sCache =
            new SparseArrayCompat<>(16);

    private final int x;
    private final int y;

    /**
     * Returns an instance of {@link AspectRatio} specified by {@code x} and {@code y} values.
     * The values {@code x} and {@code} will be reduced by their greatest common divider.
     *
     * @param width The width
     * @param height The height
     * @return An instance of {@link AspectRatio}
     */
    public static AspectRatio of(int width, int height) {
        int gcd = gcd(width, height);
        width /= gcd;
        height /= gcd;
        SparseArrayCompat<AspectRatio> arrayX = sCache.get(width);
        if (arrayX == null) {
            AspectRatio ratio = new AspectRatio(width, height);
            arrayX = new SparseArrayCompat<>();
            arrayX.put(height, ratio);
            sCache.put(width, arrayX);
            return ratio;
        } else {
            AspectRatio ratio = arrayX.get(height);
            if (ratio == null) {
                ratio = new AspectRatio(width, height);
                arrayX.put(height, ratio);
            }
            return ratio;
        }
    }

    /**
     * Parse an {@link AspectRatio} from a {@link String} formatted like "4:3".
     *
     * @param s The string representation of the aspect ratio
     * @return The aspect ratio
     * @throws IllegalArgumentException when the format is incorrect.
     */
    public static AspectRatio parse(String s) {
        int position = s.indexOf(':');
        if (position == -1) {
            throw new IllegalArgumentException("Malformed aspect ratio: " + s);
        }
        try {
            int x = Integer.parseInt(s.substring(0, position));
            int y = Integer.parseInt(s.substring(position + 1));
            return AspectRatio.of(x, y);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed aspect ratio: " + s, e);
        }
    }

    private AspectRatio(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean matches(Size size) {
        int gcd = gcd(size.getWidth(), size.getHeight());
        int x = size.getWidth() / gcd;
        int y = size.getHeight() / gcd;
        return this.x == x && this.y == y;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (this == other) {
            return true;
        }
        if (other instanceof AspectRatio) {
            AspectRatio ratio = (AspectRatio) other;
            return x == ratio.x && y == ratio.y;
        }
        return false;
    }

    @Override
    public String toString() {
        return x + ":" + y;
    }

    public float toFloat() {
        return (float) x / y;
    }

    @Override
    public int hashCode() {
        // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
        return y ^ ((x << (Integer.SIZE / 2)) | (x >>> (Integer.SIZE / 2)));
    }

    @Override
    public int compareTo(@NonNull AspectRatio other) {
        if (equals(other)) {
            return 0;
        } else if (toFloat() - other.toFloat() > 0) {
            return 1;
        }
        return -1;
    }

    /**
     * @return The inverse of this {@link AspectRatio}.
     */
    public AspectRatio inverse() {
        //noinspection SuspiciousNameCombination
        return AspectRatio.of(y, x);
    }

    /**
     * Gets the greatest common divisor
     * @param a the first number
     * @param b the second number
     * @return the greatest common divisor of a & b
     */
    private static int gcd(int a, int b) {
        while (b != 0) {
            int c = b;
            b = a % b;
            a = c;
        }
        return a;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(x);
        dest.writeInt(y);
    }

    public static final Creator<AspectRatio> CREATOR = new Creator<AspectRatio>() {

        @Override
        public AspectRatio createFromParcel(Parcel source) {
            int x = source.readInt();
            int y = source.readInt();
            return AspectRatio.of(x, y);
        }

        @Override
        public AspectRatio[] newArray(int size) {
            return new AspectRatio[size];
        }
    };
}
