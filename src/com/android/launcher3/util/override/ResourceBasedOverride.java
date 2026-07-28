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

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

/** Marker interface for implementations selected through a string resource. */
public interface ResourceBasedOverride {

    /** Utilities for instantiating resource-selected implementations. */
    final class Overrides {
        private static final String TAG = "Overrides";

        private Overrides() {}

        /**
         * Returns the implementation named by {@code resId}, falling back to the supplied class.
         */
        public static <T extends ResourceBasedOverride> T getObject(
                Class<T> defaultClass, Context context, int resId) {
            String className = context.getString(resId);
            if (TextUtils.isEmpty(className)) {
                className = defaultClass.getName();
            }

            try {
                Class<?> clazz = Class.forName(className);
                return defaultClass.cast(newInstance(clazz, context));
            } catch (ReflectiveOperationException | ClassCastException e) {
                Log.e(TAG, "Unable to create override " + className
                        + ", using " + defaultClass.getName(), e);
                try {
                    return defaultClass.cast(newInstance(defaultClass, context));
                } catch (ReflectiveOperationException fallbackException) {
                    throw new RuntimeException("Unable to create " + defaultClass.getName(),
                            fallbackException);
                }
            }
        }

        private static Object newInstance(Class<?> clazz, Context context)
                throws ReflectiveOperationException {
            try {
                return clazz.getDeclaredConstructor(Context.class).newInstance(context);
            } catch (NoSuchMethodException e) {
                return clazz.getDeclaredConstructor().newInstance();
            }
        }
    }
}
