/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.util.override;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Looper;

import java.util.concurrent.ExecutionException;

/** Lazily creates and stores an object on the main thread. */
public class MainThreadInitializedObject<T> {

    private final ObjectProvider<T> mProvider;
    private T mValue;

    public MainThreadInitializedObject(ObjectProvider<T> provider) {
        mProvider = provider;
    }

    /** Returns the singleton value, initializing it on the main thread when necessary. */
    public T get(Context context) {
        if (mValue != null) {
            return mValue;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            return initialize(context);
        }

        try {
            return MAIN_EXECUTOR.submit(() -> initialize(context)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private synchronized T initialize(Context context) {
        if (mValue == null) {
            mValue = mProvider.get(context.getApplicationContext());
        }
        return mValue;
    }

    /** Creates a singleton backed by a resource-selected implementation. */
    public static <T extends ResourceBasedOverride> MainThreadInitializedObject<T> forOverride(
            Class<T> defaultClass, int resId) {
        return new MainThreadInitializedObject<>(context ->
                ResourceBasedOverride.Overrides.getObject(defaultClass, context, resId));
    }

    /** Factory used to initialize the singleton. */
    public interface ObjectProvider<T> {
        T get(Context context);
    }
}
