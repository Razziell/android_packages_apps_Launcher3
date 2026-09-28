/*
 * Copyright (C) 2026 The Evolution X Project
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
package com.android.launcher3.taskbar;

import java.util.function.LongSupplier;

/**
 * Idle policy for a phone navigation handle. All calls must use the owning View's thread.
 * Only controls its own visual alpha factor: never changes the window or swipe region.
 */
final class NavHandleAutoHideController {
    interface Scheduler {
        void postDelayed(Runnable action, long delayMillis);
        void removeCallbacks(Runnable action);
    }

    private final Scheduler mScheduler;
    private final LongSupplier mTimeout;
    private final Runnable mShow;
    private final Runnable mHide;
    private final Runnable mHideAfterIdle;
    private volatile boolean mEnabled;
    private boolean mScreenOn = true;
    private boolean mTouchActive;
    private volatile boolean mDestroyed;

    NavHandleAutoHideController(Scheduler scheduler, LongSupplier timeout,
            Runnable show, Runnable hide) {
        mScheduler = scheduler;
        mTimeout = timeout;
        mShow = show;
        mHide = hide;
        mHideAfterIdle = () -> {
            if (!mDestroyed && mEnabled && mScreenOn && !mTouchActive) {
                mHide.run();
            }
        };
    }

    void setEnabled(boolean enabled) {
        if (mDestroyed || mEnabled == enabled) return;
        mEnabled = enabled;
        showTemporarily();
    }

    boolean isEnabled() {
        return mEnabled && !mDestroyed;
    }

    void setScreenOn(boolean screenOn) {
        if (mDestroyed) return;
        mScreenOn = screenOn;
        if (!screenOn) mTouchActive = false;
        showTemporarily();
    }

    void onTouchStarted() {
        if (mDestroyed) return;
        mTouchActive = true;
        showTemporarily();
    }

    void onTouchFinished() {
        if (mDestroyed || !mTouchActive) return;
        mTouchActive = false;
        showTemporarily();
    }

    void showTemporarily() {
        if (mDestroyed) return;
        mScheduler.removeCallbacks(mHideAfterIdle);
        mShow.run();
        if (mEnabled && mScreenOn && !mTouchActive) {
            mScheduler.postDelayed(mHideAfterIdle, Math.max(0L, mTimeout.getAsLong()));
        }
    }

    void destroy() {
        mDestroyed = true;
        mScheduler.removeCallbacks(mHideAfterIdle);
    }
}
