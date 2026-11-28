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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

import androidx.preference.PreferenceManager;

import java.util.Collections;

public class GameBarMonitorService extends Service {

    private static final long MONITOR_INTERVAL_MS = 2000;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    static void update(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final Intent intent = new Intent(context, GameBarMonitorService.class);
        final GameBar bar = GameBar.getInstance(context);
        if (!Settings.canDrawOverlays(context)) {
            context.stopService(intent);
            bar.hide();
            return;
        }

        if (prefs.getBoolean("game_bar_auto_enable", false)) {
            context.startService(intent);
        } else {
            context.stopService(intent);
        }

        if (prefs.getBoolean("game_bar_enable", false)) {
            bar.show();
        } else if (!prefs.getBoolean("game_bar_auto_enable", false)) {
            bar.hide();
        }
    }

    private final Runnable mMonitor = new Runnable() {
        @Override
        public void run() {
            monitorForegroundApp();
            mHandler.postDelayed(this, MONITOR_INTERVAL_MS);
        }
    };

    private void monitorForegroundApp() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final GameBar bar = GameBar.getInstance(this);
        final String foreground = ForegroundAppDetector.getForegroundPackageName();
        final boolean showForApp = prefs.getStringSet(
                GameBarPerAppConfigFragment.PREF_AUTO_APPS, Collections.emptySet())
                .contains(foreground);
        if (Settings.canDrawOverlays(this) && (prefs.getBoolean("game_bar_enable", false)
                || (prefs.getBoolean("game_bar_auto_enable", false) && showForApp))) {
            bar.show();
        } else {
            bar.hide();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mHandler.removeCallbacks(mMonitor);
        mMonitor.run();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mMonitor);
        super.onDestroy();
    }
}
