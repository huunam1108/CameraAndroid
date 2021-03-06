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

import android.support.v4.util.ArrayMap;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A collection class that automatically groups {@link Size}s by their {@link AspectRatio}s.
 */
public class SizeMap {

    private final ArrayMap<AspectRatio, SortedSet<Size>> ratios = new ArrayMap<>();

    /**
     * Add a new {@link Size} to this collection.
     *
     * @param size The size to add.
     * @return {@code true} if it is added, {@code false} if it already exists and is not added.
     */
    public boolean add(Size size) {
        for (AspectRatio ratio : ratios.keySet()) {
            if (ratio.matches(size)) {
                final SortedSet<Size> sizes = ratios.get(ratio);
                if (sizes.contains(size)) {
                    return false;
                } else {
                    sizes.add(size);
                    return true;
                }
            }
        }
        // None of the existing ratio matches the provided size; add a new key
        SortedSet<Size> sizes = new TreeSet<>();
        sizes.add(size);
        ratios.put(AspectRatio.of(size.getWidth(), size.getHeight()), sizes);
        return true;
    }

    /**
     * Removes the specified aspect ratio and all sizes associated with it.
     *
     * @param ratio The aspect ratio to be removed.
     */
    public void remove(AspectRatio ratio) {
        ratios.remove(ratio);
    }

    public Set<AspectRatio> ratios() {
        return ratios.keySet();
    }

    public SortedSet<Size> sizes(AspectRatio ratio) {
        return ratios.get(ratio);
    }

    public void clear() {
        ratios.clear();
    }

    public boolean isEmpty() {
        return ratios.isEmpty();
    }
}
