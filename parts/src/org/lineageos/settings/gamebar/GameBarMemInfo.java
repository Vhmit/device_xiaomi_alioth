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

import android.app.ActivityManager;
import android.content.Context;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class GameBarMemInfo {

    public static String getRamUsage(Context context) {
        final ActivityManager manager = context.getSystemService(ActivityManager.class);
        final ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        if (info.totalMem <= 0) {
            return "N/A";
        }
        return Long.toString((info.totalMem - info.availMem) / (1024 * 1024));
    }

    public static String getRamSpeed() {
        String path = "/sys/devices/system/cpu/bus_dcvs/DDR/cur_freq";
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine();
            if (line != null && !line.isEmpty()) {
                try {
                    int khz = Integer.parseInt(line.trim());
                    float mhz = khz / 1000f;
                    if (mhz >= 1000) {
                        return String.format("%.3f GHz", mhz / 1000f);
                    } else {
                        return String.format("%.0f MHz", mhz);
                    }
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            // ignore
        }
        return "N/A";
    }
}
