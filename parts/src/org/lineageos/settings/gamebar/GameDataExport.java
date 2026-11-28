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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import org.lineageos.settings.R;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

public class GameDataExport {

    private static GameDataExport sInstance;
    public static synchronized GameDataExport getInstance() {
        if (sInstance == null) {
            sInstance = new GameDataExport();
        }
        return sInstance;
    }

    private boolean mCapturing = false;

    private final List<String[]> mStatsRows = new ArrayList<>();

    private static final String[] CSV_HEADER = {
            "DateTime",
            "PackageName",
            "FPS",
            "Battery_Temp",
            "CPU_Usage",
            "CPU_Temp",
            "GPU_Usage",
            "GPU_Clock",
            "GPU_Temp"
    };

    private GameDataExport() {
    }

    public void startCapture() {
        mCapturing = true;
        mStatsRows.clear();
        mStatsRows.add(CSV_HEADER);
    }

    public void stopCapture() {
        mCapturing = false;
    }

    public boolean isCapturing() {
        return mCapturing;
    }

    public void addOverlayData(String dateTime,
                               String packageName,
                               String fps,
                               String batteryTemp,
                               String cpuUsage,
                               String cpuTemp,
                               String gpuUsage,
                               String gpuClock,
                               String gpuTemp) {
        if (!mCapturing) return;

        String[] row = {
                dateTime,
                packageName,
                fps,
                batteryTemp,
                cpuUsage,
                cpuTemp,
                gpuUsage,
                gpuClock,
                gpuTemp
        };
        mStatsRows.add(row);
    }

    public void exportDataToCsv(Context context, IntConsumer onComplete) {
        if (mStatsRows.size() <= 1) {
            onComplete.accept(R.string.game_bar_capture_empty);
            return;
        }
        final List<String[]> snapshot = new ArrayList<>(mStatsRows);
        CompletableFuture.supplyAsync(() -> writeToDownloads(context.getContentResolver(), snapshot))
                .thenAcceptAsync(onComplete::accept, context.getMainExecutor());
    }

    private static int writeToDownloads(ContentResolver resolver, List<String[]> rows) {
        final String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT)
                .format(new Date());
        final ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, "GameBar_log_" + timestamp + ".csv");
        values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri destination = null;
        try {
            destination = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (destination == null) {
                return R.string.game_bar_capture_export_failed;
            }
            final OutputStream output = resolver.openOutputStream(destination, "w");
            if (output == null) {
                throw new IOException("Could not open the CSV file");
            }
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                for (String[] row : rows) {
                    writer.write(toCsvLine(row));
                    writer.newLine();
                }
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            if (resolver.update(destination, values, null, null) == 0) {
                throw new IOException("Could not publish the CSV file");
            }
            return R.string.game_bar_capture_exported;
        } catch (IOException | RuntimeException e) {
            Log.w("GameDataExport", "Could not export metrics", e);
            if (destination != null) {
                try {
                    resolver.delete(destination, null, null);
                } catch (RuntimeException cleanupError) {
                    Log.w("GameDataExport", "Could not remove incomplete export", cleanupError);
                }
            }
            return R.string.game_bar_capture_export_failed;
        }
    }

    private static String toCsvLine(String[] columns) {
        final StringBuilder line = new StringBuilder();
        for (String column : columns) {
            if (line.length() > 0) {
                line.append(',');
            }
            line.append('"').append(column.replace("\"", "\"\"")).append('"');
        }
        return line.toString();
    }
}
