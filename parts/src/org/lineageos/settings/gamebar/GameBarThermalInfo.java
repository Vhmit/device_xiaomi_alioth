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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class GameBarThermalInfo {

    private static final File THERMAL_DIR = new File("/sys/class/thermal");
    private static final Map<File, String> ZONE_TYPES = new HashMap<>();

    private GameBarThermalInfo() {}

    static String readTemperature(String prefix) {
        if (ZONE_TYPES.isEmpty()) {
            final File[] zones = THERMAL_DIR.listFiles((dir, name) -> name.matches("thermal_zone[0-9]+"));
            if (zones != null) {
                for (File zone : zones) {
                    try (BufferedReader reader = new BufferedReader(
                            new FileReader(new File(zone, "type")))) {
                        final String type = reader.readLine();
                        if (type != null) {
                            ZONE_TYPES.put(zone, type.trim());
                        }
                    } catch (IOException ignored) {}
                }
            }
        }

        float maximum = Float.NEGATIVE_INFINITY;
        for (Map.Entry<File, String> entry : ZONE_TYPES.entrySet()) {
            final String type = entry.getValue();
            if (!type.startsWith(prefix) || !type.endsWith("-usr")) {
                continue;
            }
            try (BufferedReader reader = new BufferedReader(
                    new FileReader(new File(entry.getKey(), "temp")))) {
                final String temperature = reader.readLine();
                if (temperature != null) {
                    maximum = Math.max(maximum, Integer.parseInt(temperature.trim()) / 1000f);
                }
            } catch (IOException | NumberFormatException ignored) {}
        }

        return Float.isFinite(maximum) ? String.format(Locale.getDefault(), "%.1f", maximum) : "N/A";
    }
}
