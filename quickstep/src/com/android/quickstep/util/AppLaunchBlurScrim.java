/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.quickstep.util;

import android.util.Log;
import android.view.CrossWindowBlurListeners;
import android.view.SurfaceControl;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Owns one optional app-launch/close effect layer. All calls run on the main thread. */
public final class AppLaunchBlurScrim implements AutoCloseable,
        View.OnAttachStateChangeListener {
    private static final String TAG = "AppLaunchBlurScrim";

    private final View mView;
    private final BooleanSupplier mBlurAllowed;
    private final Consumer<Boolean> mBlurListener = enabled -> {
        if (!enabled) {
            close();
        }
    };
    private SurfaceControl mSurface;
    private boolean mListening;
    private boolean mClosed;

    private AppLaunchBlurScrim(View view, BooleanSupplier blurAllowed) {
        mView = view;
        mBlurAllowed = blurAllowed;
    }

    /** Returns null when the optional effect cannot be created; never owns the parent leash. */
    @Nullable
    public static AppLaunchBlurScrim create(View view, @Nullable SurfaceControl parent,
            BooleanSupplier blurAllowed) {
        if (!view.isAttachedToWindow() || parent == null || !parent.isValid()
                || !blurAllowed.getAsBoolean()) {
            return null;
        }
        AppLaunchBlurScrim scrim = new AppLaunchBlurScrim(view, blurAllowed);
        try {
            // Attach at creation, not in a deferred frame transaction. Queued property updates
            // must never reparent a layer after close() has detached it.
            scrim.mSurface = new SurfaceControl.Builder()
                    .setName("App transition background scrim")
                    .setCallsite("AppLaunchBlurScrim.create")
                    .setParent(parent)
                    .setEffectLayer()
                    .setOpaque(false)
                    .setHidden(true)
                    .build();
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                transaction.setColor(scrim.mSurface, new float[] {0f, 0f, 0f})
                        .setLayer(scrim.mSurface, 1000)
                        .setAlpha(scrim.mSurface, 0f)
                        .apply();
            }
            view.addOnAttachStateChangeListener(scrim);
            scrim.mListening = true;
            CrossWindowBlurListeners.getInstance().addListener(
                    view.getContext().getMainExecutor(), scrim.mBlurListener);
            return scrim.mClosed ? null : scrim;
        } catch (RuntimeException e) {
            // An optional visual effect must not abort the remote app animation.
            Log.w(TAG, "Cannot create blur scrim", e);
            scrim.close();
            return null;
        }
    }

    /** Appends only property updates; a disabled effect stays closed for this transition. */
    public void update(SurfaceTransaction transaction, float alpha, int blurRadius) {
        if (mClosed) {
            return;
        }
        if (!mView.isAttachedToWindow() || !mSurface.isValid()
                || !mBlurAllowed.getAsBoolean()) {
            close();
            return;
        }
        transaction.forSurface(mSurface)
                .setAlpha(Float.isFinite(alpha) ? Math.max(0f, Math.min(1f, alpha)) : 0f)
                .setBackgroundBlurRadius(Math.max(0, blurRadius))
                .setShow();
    }

    @Override
    public void close() {
        if (mClosed) {
            return;
        }
        mClosed = true;
        mView.removeOnAttachStateChangeListener(this);
        if (mListening) {
            mListening = false;
            try {
                CrossWindowBlurListeners.getInstance().removeListener(mBlurListener);
            } catch (RuntimeException e) {
                Log.w(TAG, "Cannot unregister blur listener", e);
            }
        }
        SurfaceControl surface = mSurface;
        mSurface = null;
        if (surface == null) {
            return;
        }
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            if (surface.isValid()) {
                // Do not depend on another ViewRoot frame (which may never arrive). Earlier
                // queued alpha/blur/show updates cannot reattach this removed layer.
                transaction.remove(surface).apply();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot remove blur scrim", e);
        } finally {
            surface.release();
        }
    }

    @Override
    public void onViewAttachedToWindow(View view) { }

    @Override
    public void onViewDetachedFromWindow(View view) {
        close();
    }
}
