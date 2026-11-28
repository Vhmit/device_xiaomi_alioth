/*
 * Copyright (C) 2025 kenway214
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

package org.lineageos.settings.gamebar;

import android.app.ActivityTaskManager.RootTaskInfo;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.window.TaskFpsCallback;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

final class GameBarFpsMeter {

    private static final long TASK_CHECK_INTERVAL_MS = 1000;
    private static final String LEGACY_FPS_PATH = "/sys/class/drm/sde-crtc-0/measured_fps";

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private int mCurrentTaskId = -1;
    private float mCurrentFps = -1f;
    private TaskFpsCallback mCallback;

    GameBarFpsMeter(Context context) {
        mContext = context;
        mWindowManager = context.getSystemService(WindowManager.class);
    }

    private final Runnable mCheckTask = new Runnable() {
        @Override
        public void run() {
            if (usesLegacyFps()) {
                unregisterCallback();
            } else {
                final RootTaskInfo task = ForegroundAppDetector.getFocusedTask();
                final int taskId = task != null ? task.taskId : -1;
                if (taskId != mCurrentTaskId) {
                    unregisterCallback();
                    if (taskId >= 0) {
                        registerCallback(taskId);
                    }
                }
            }
            mHandler.postDelayed(this, TASK_CHECK_INTERVAL_MS);
        }
    };

    void start() {
        mHandler.removeCallbacks(mCheckTask);
        mCheckTask.run();
    }

    void stop() {
        mHandler.removeCallbacks(mCheckTask);
        unregisterCallback();
    }

    float getFps() {
        return usesLegacyFps() ? readLegacyFps() : mCurrentFps;
    }

    private boolean usesLegacyFps() {
        return "legacy".equals(PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString("game_bar_fps_method", "new"));
    }

    private void registerCallback(int taskId) {
        final TaskFpsCallback callback = new TaskFpsCallback() {
            @Override
            public void onFpsReported(float fps) {
                if (mCallback == this) {
                    mCurrentFps = fps;
                }
            }
        };
        try {
            mWindowManager.registerTaskFpsCallback(taskId, mContext.getMainExecutor(), callback);
            mCallback = callback;
            mCurrentTaskId = taskId;
        } catch (IllegalArgumentException | SecurityException ignored) {
            // The task can disappear between querying it and registering the callback.
        }
    }

    private void unregisterCallback() {
        if (mCallback != null) {
            mWindowManager.unregisterTaskFpsCallback(mCallback);
            mCallback = null;
        }
        mCurrentTaskId = -1;
        mCurrentFps = -1f;
    }

    private float readLegacyFps() {
        try (BufferedReader reader = new BufferedReader(new FileReader(LEGACY_FPS_PATH))) {
            final String line = reader.readLine();
            if (line != null) {
                final String value = line.trim();
                final String fps = value.startsWith("fps:")
                        ? value.substring("fps:".length()).trim() : value;
                return Float.parseFloat(fps.split("\\s+", 2)[0]);
            }
        } catch (IOException | NumberFormatException ignored) {}
        return -1f;
    }
}
