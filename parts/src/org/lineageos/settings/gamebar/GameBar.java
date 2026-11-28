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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GameBar {

    private static GameBar sInstance;
    public static synchronized GameBar getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new GameBar(context.getApplicationContext());
        }
        return sInstance;
    }

    private static final String TAG = "GameBar";

    private static final String PREF_KEY_X = "game_bar_x";
    private static final String PREF_KEY_Y = "game_bar_y";

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mHandler;
    private final GameBarFpsMeter mFpsMeter;

    private View mOverlayView;
    private LinearLayout mRootLayout;
    private WindowManager.LayoutParams mLayoutParams;
    private boolean mIsShowing = false;

    private int mTextSizeSp       = 16;
    private int mBackgroundAlpha  = 128;
    private int mCornerRadius     = 16;
    private int mPaddingDp        = 12;
    private String mTitleColorHex = "#FFFFFF";
    private String mValueColorHex = "#FFFFFF";
    private String mOverlayFormat = "full";
    private String mPosition      = "top_left";
    private String mSplitMode     = "stacked";
    private int mUpdateIntervalMs = 1000;
    private boolean mDraggable    = false;

    private boolean mShowBatteryTemp = false;
    private boolean mShowCpuUsage    = false;
    private boolean mShowCpuClock    = false;
    private boolean mShowCpuTemp     = false;
    private boolean mShowRam         = false;
    private boolean mShowFps         = false;

    private boolean mShowGpuUsage    = false;
    private boolean mShowGpuClock    = false;
    private boolean mShowGpuTemp     = false;

    private boolean mLongPressEnabled      = false;
    private long mLongPressThresholdMs = 1000;
    private boolean mPressActive           = false;
    private float mDownX, mDownY;
    private static final float TOUCH_SLOP = 20f;

    private GestureDetector mGestureDetector;
    private boolean mDoubleTapCaptureEnabled = false;
    private boolean mSingleTapToggleEnabled  = false;
    private GradientDrawable mBgDrawable;

    private int mItemSpacingDp = 8;

    private boolean mShowRamSpeed = false;
    private boolean mShowRamTemp = false;

    private final Runnable mLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPressActive) {
                openOverlaySettings();
                mPressActive = false;
            }
        }
    };

    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsShowing) {
                updateStats();
                mHandler.postDelayed(this, mUpdateIntervalMs);
            }
        }
    };

    private GameBar(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        mFpsMeter = new GameBarFpsMeter(mContext);

        mBgDrawable = new GradientDrawable();
        applyBackgroundStyle();

        mGestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (mDoubleTapCaptureEnabled) {
                    if (GameDataExport.getInstance().isCapturing()) {
                        GameDataExport.getInstance().stopCapture();
                        Toast.makeText(mContext, R.string.game_bar_capture_stopped, Toast.LENGTH_SHORT).show();
                    } else {
                        GameDataExport.getInstance().startCapture();
                        Toast.makeText(mContext, R.string.game_bar_capture_started, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return super.onDoubleTap(e);
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (mSingleTapToggleEnabled) {
                    mOverlayFormat = "full".equals(mOverlayFormat) ? "minimal" : "full";
                    PreferenceManager.getDefaultSharedPreferences(mContext)
                        .edit()
                        .putString("game_bar_format", mOverlayFormat)
                        .apply();
                    final int message = "full".equals(mOverlayFormat)
                            ? R.string.game_bar_format_changed_full
                            : R.string.game_bar_format_changed_minimal;
                    Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
                    updateStats();
                    return true;
                }
                return super.onSingleTapConfirmed(e);
            }
        });
    }

    public void applyPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        mShowFps         = prefs.getBoolean("game_bar_fps_enable", true);
        mShowBatteryTemp = prefs.getBoolean("game_bar_temp_enable", false);
        mShowCpuUsage    = prefs.getBoolean("game_bar_cpu_usage_enable", false);
        mShowCpuClock    = prefs.getBoolean("game_bar_cpu_clock_enable", false);
        mShowCpuTemp     = prefs.getBoolean("game_bar_cpu_temp_enable", false);
        mShowRam         = prefs.getBoolean("game_bar_ram_enable", false);

        mShowGpuUsage    = prefs.getBoolean("game_bar_gpu_usage_enable", false);
        mShowGpuClock    = prefs.getBoolean("game_bar_gpu_clock_enable", false);
        mShowGpuTemp     = prefs.getBoolean("game_bar_gpu_temp_enable", false);

        mDoubleTapCaptureEnabled = prefs.getBoolean("game_bar_doubletap_capture", false);
        mSingleTapToggleEnabled  = prefs.getBoolean("game_bar_single_tap_toggle", false);

        mShowRamSpeed    = prefs.getBoolean("game_bar_ram_speed_enable", false);
        mShowRamTemp     = prefs.getBoolean("game_bar_ram_temp_enable", false);

        mSplitMode = prefs.getString("game_bar_split_mode", "stacked");
        mTextSizeSp = prefs.getInt("game_bar_text_size", 16);
        mBackgroundAlpha = prefs.getInt("game_bar_background_alpha", 128);
        mCornerRadius = prefs.getInt("game_bar_corner_radius", 16);
        mPaddingDp = prefs.getInt("game_bar_padding", 12);
        mTitleColorHex = prefs.getString("game_bar_title_color", "#FFFFFF");
        mValueColorHex = prefs.getString("game_bar_value_color", "#4CAF50");
        mOverlayFormat = prefs.getString("game_bar_format", "full");
        mUpdateIntervalMs = Math.max(500, readDuration(prefs, "game_bar_update_interval"));
        mPosition = prefs.getString("game_bar_position", "top_left");
        mItemSpacingDp = prefs.getInt("game_bar_item_spacing", 8);
        mLongPressEnabled = prefs.getBoolean("game_bar_longpress_enable", false);
        mLongPressThresholdMs = readDuration(prefs, "game_bar_longpress_timeout");

        if (mIsShowing) {
            applyPosition();
            applyBackgroundStyle();
            applyPadding();
            if (mOverlayView.isAttachedToWindow()) {
                mWindowManager.updateViewLayout(mOverlayView, mLayoutParams);
            }
            startUpdates();
        }
    }

    private static int readDuration(SharedPreferences prefs, String key) {
        try {
            return Integer.parseInt(prefs.getString(key, "1000"));
        } catch (NumberFormatException e) {
            return 1000;
        }
    }

    public void show() {
        if (mIsShowing || !Settings.canDrawOverlays(mContext)) return;

        applyPreferences();

        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        applyPosition();

        mOverlayView = new LinearLayout(mContext);
        mOverlayView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        mRootLayout = (LinearLayout) mOverlayView;
        applyBackgroundStyle();
        applyPadding();

        mOverlayView.setOnTouchListener((v, event) -> {
            if (mDoubleTapCaptureEnabled || mSingleTapToggleEnabled) {
                mGestureDetector.onTouchEvent(event);
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (mDraggable) {
                        initialX = mLayoutParams.x;
                        initialY = mLayoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                    }
                    if (mLongPressEnabled) {
                        mPressActive = true;
                        mDownX = event.getRawX();
                        mDownY = event.getRawY();
                        mHandler.postDelayed(mLongPressRunnable, mLongPressThresholdMs);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (mLongPressEnabled && mPressActive) {
                        float dx = Math.abs(event.getRawX() - mDownX);
                        float dy = Math.abs(event.getRawY() - mDownY);
                        if (dx > TOUCH_SLOP || dy > TOUCH_SLOP) {
                            mPressActive = false;
                            mHandler.removeCallbacks(mLongPressRunnable);
                        }
                    }
                    if (mDraggable) {
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        mLayoutParams.x = initialX + deltaX;
                        mLayoutParams.y = initialY + deltaY;
                        mWindowManager.updateViewLayout(mOverlayView, mLayoutParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mLongPressEnabled && mPressActive) {
                        mPressActive = false;
                        mHandler.removeCallbacks(mLongPressRunnable);
                    }
                    if (mDraggable) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                        prefs.edit()
                                .putInt(PREF_KEY_X, mLayoutParams.x)
                                .putInt(PREF_KEY_Y, mLayoutParams.y)
                                .apply();
                    }
                    return true;
            }
            return false;
        });

        try {
            mWindowManager.addView(mOverlayView, mLayoutParams);
        } catch (WindowManager.BadTokenException | SecurityException e) {
            Log.w(TAG, "Could not show the overlay", e);
            mOverlayView = null;
            mRootLayout = null;
            return;
        }
        mIsShowing = true;
        mFpsMeter.start();
        startUpdates();
    }

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    public void hide() {
        if (!mIsShowing) return;
        mHandler.removeCallbacksAndMessages(null);
        mIsShowing = false;
        mPressActive = false;
        mFpsMeter.stop();
        try {
            mWindowManager.removeView(mOverlayView);
        } catch (IllegalArgumentException ignored) {
            // The window can already have been removed after permission changes.
        }
        mOverlayView = null;
        mRootLayout = null;
    }

    private void updateStats() {
        if (!mIsShowing || mRootLayout == null) return;

        mRootLayout.removeAllViews();

        List<View> statViews = new ArrayList<>();

        // 1) FPS
        float fpsVal = mFpsMeter.getFps();
        String fpsStr = fpsVal >= 0 ? String.format(Locale.getDefault(), "%.0f", fpsVal) : "N/A";
        if (mShowFps) {
            statViews.add(createStatLine(R.string.game_bar_stat_fps, fpsStr));
        }

        // 2) Battery temp
        String batteryTempStr = "N/A";
        if (mShowBatteryTemp) {
            final Intent battery = mContext.registerReceiver(
                    null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            final int temperature = battery == null ? Integer.MIN_VALUE
                    : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (temperature != Integer.MIN_VALUE) {
                batteryTempStr = String.format(Locale.getDefault(), "%.1f", temperature / 10f);
            }
            statViews.add(createStatLine(R.string.game_bar_stat_battery_temp,
                    withUnit(batteryTempStr, "°C")));
        }

        // 3) CPU usage
        String cpuUsageStr = "N/A";
        if (mShowCpuUsage) {
            cpuUsageStr = GameBarCpuInfo.getCpuUsage();
            String display = withUnit(cpuUsageStr, "%");
            statViews.add(createStatLine(R.string.game_bar_stat_cpu, display));
        }

        // 4) CPU freq
        if (mShowCpuClock) {
            List<String> freqs = GameBarCpuInfo.getCpuFrequencies(mContext);
            statViews.add(freqs.isEmpty()
                    ? createStatLine(R.string.game_bar_stat_cpu_frequency, "N/A")
                    : buildCpuFreqView(freqs));
        }

        // 5) CPU temp
        String cpuTempStr = "N/A";
        if (mShowCpuTemp) {
            cpuTempStr = GameBarThermalInfo.readTemperature("cpu-");
            statViews.add(createStatLine(R.string.game_bar_stat_cpu_temp, withUnit(cpuTempStr, "°C")));
        }

        // 6) RAM usage
        String ramStr = "N/A";
        if (mShowRam) {
            ramStr = GameBarMemInfo.getRamUsage(mContext);
            statViews.add(createStatLine(R.string.game_bar_stat_ram, withUnit(ramStr, " MB")));
        }

        // 6.1) RAM speed
        if (mShowRamSpeed) {
            String ramSpeedStr = GameBarMemInfo.getRamSpeed();
            statViews.add(createStatLine(R.string.game_bar_stat_ram_frequency, ramSpeedStr));
        }

        // 6.2) RAM temp
        if (mShowRamTemp) {
            String ramTempStr = GameBarThermalInfo.readTemperature("ddr-");
            statViews.add(createStatLine(R.string.game_bar_stat_ram_temp, withUnit(ramTempStr, "°C")));
        }

        // 7) GPU usage
        String gpuUsageStr = "N/A";
        if (mShowGpuUsage) {
            gpuUsageStr = GameBarGpuInfo.getGpuUsage();
            statViews.add(createStatLine(R.string.game_bar_stat_gpu, withUnit(gpuUsageStr, "%")));
        }

        // 8) GPU clock
        String gpuClockStr = "N/A";
        if (mShowGpuClock) {
            gpuClockStr = GameBarGpuInfo.getGpuClock();
            statViews.add(createStatLine(R.string.game_bar_stat_gpu_frequency, withUnit(gpuClockStr, " MHz")));
        }

        // 9) GPU temp
        String gpuTempStr = "N/A";
        if (mShowGpuTemp) {
            gpuTempStr = GameBarThermalInfo.readTemperature("gpuss-");
            statViews.add(createStatLine(R.string.game_bar_stat_gpu_temp, withUnit(gpuTempStr, "°C")));
        }

        if ("side_by_side".equals(mSplitMode)) {
            mRootLayout.setOrientation(LinearLayout.HORIZONTAL);
            if ("minimal".equals(mOverlayFormat)) {
                for (int i = 0; i < statViews.size(); i++) {
                    mRootLayout.addView(statViews.get(i));
                    if (i < statViews.size() - 1) {
                        mRootLayout.addView(createDotView());
                    }
                }
            } else {
                for (View view : statViews) {
                    mRootLayout.addView(view);
                }
            }
        } else {
            mRootLayout.setOrientation(LinearLayout.VERTICAL);
            for (View view : statViews) {
                mRootLayout.addView(view);
            }
        }

        if (GameDataExport.getInstance().isCapturing()) {
            String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String pkgName = ForegroundAppDetector.getForegroundPackageName();

            GameDataExport.getInstance().addOverlayData(
                    dateTime,
                    pkgName,
                    fpsStr,
                    batteryTempStr,
                    cpuUsageStr,
                    cpuTempStr,
                    gpuUsageStr,
                    gpuClockStr,
                    gpuTempStr
            );
        }

    }

    private View buildCpuFreqView(List<String> freqs) {
        LinearLayout freqContainer = new LinearLayout(mContext);
        freqContainer.setOrientation(LinearLayout.HORIZONTAL);

        int spacingPx = dpToPx(mContext, mItemSpacingDp);
        LinearLayout.LayoutParams outerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        outerLp.setMargins(spacingPx, spacingPx / 2, spacingPx, spacingPx / 2);
        freqContainer.setLayoutParams(outerLp);

        if ("full".equals(mOverlayFormat)) {
            TextView labelTv = new TextView(mContext);
            labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);
            try {
                labelTv.setTextColor(Color.parseColor(mTitleColorHex));
            } catch (Exception e) {
                labelTv.setTextColor(Color.WHITE);
            }
            labelTv.setText(mContext.getString(R.string.game_bar_stat_cpu_frequency) + " ");
            freqContainer.addView(labelTv);
        }

        LinearLayout verticalFreqs = new LinearLayout(mContext);
        verticalFreqs.setOrientation(LinearLayout.VERTICAL);

        for (String freqLine : freqs) {
            LinearLayout lineLayout = new LinearLayout(mContext);
            lineLayout.setOrientation(LinearLayout.HORIZONTAL);

            TextView freqTv = new TextView(mContext);
            freqTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);
            try {
                freqTv.setTextColor(Color.parseColor(mValueColorHex));
            } catch (Exception e) {
                freqTv.setTextColor(Color.WHITE);
            }
            freqTv.setText(freqLine);

            lineLayout.addView(freqTv);

            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lineLp.setMargins(spacingPx, spacingPx / 4, spacingPx, spacingPx / 4);
            lineLayout.setLayoutParams(lineLp);

            verticalFreqs.addView(lineLayout);
        }

        freqContainer.addView(verticalFreqs);
        return freqContainer;
    }

    private static String withUnit(String value, String unit) {
        return "N/A".equals(value) ? value : value + unit;
    }

    private LinearLayout createStatLine(int label, String rawValue) {
        final String title = mContext.getString(label);
        final String value = "N/A".equals(rawValue)
                ? mContext.getString(R.string.game_bar_stat_unavailable) : rawValue;
        LinearLayout lineLayout = new LinearLayout(mContext);
        lineLayout.setOrientation(LinearLayout.HORIZONTAL);

        if ("full".equals(mOverlayFormat)) {
            TextView tvTitle = new TextView(mContext);
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);
            try {
                tvTitle.setTextColor(Color.parseColor(mTitleColorHex));
            } catch (Exception e) {
                tvTitle.setTextColor(Color.WHITE);
            }
            tvTitle.setText(title.isEmpty() ? "" : title + " ");

            TextView tvValue = new TextView(mContext);
            tvValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);
            try {
                tvValue.setTextColor(Color.parseColor(mValueColorHex));
            } catch (Exception e) {
                tvValue.setTextColor(Color.WHITE);
            }
            tvValue.setText(value);

            lineLayout.addView(tvTitle);
            lineLayout.addView(tvValue);
        } else {
            TextView tvMinimal = new TextView(mContext);
            tvMinimal.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);
            try {
                tvMinimal.setTextColor(Color.parseColor(mValueColorHex));
            } catch (Exception e) {
                tvMinimal.setTextColor(Color.WHITE);
            }
            tvMinimal.setText(value);
            lineLayout.addView(tvMinimal);
        }

        int spacingPx = dpToPx(mContext, mItemSpacingDp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(spacingPx, spacingPx / 2, spacingPx, spacingPx / 2);
        lineLayout.setLayoutParams(lp);

        return lineLayout;
    }

    private View createDotView() {
        TextView dotView = new TextView(mContext);
        dotView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);
        try {
            dotView.setTextColor(Color.parseColor(mValueColorHex));
        } catch (Exception e) {
            dotView.setTextColor(Color.WHITE);
        }
        dotView.setText(" . ");
        return dotView;
    }

    private void applyBackgroundStyle() {
        int color = Color.argb(mBackgroundAlpha, 0, 0, 0);
        mBgDrawable.setColor(color);
        mBgDrawable.setCornerRadius(mCornerRadius);

        if (mOverlayView != null) {
            mOverlayView.setBackground(mBgDrawable);
        }
    }

    private void applyPadding() {
        if (mRootLayout != null) {
            int px = dpToPx(mContext, mPaddingDp);
            mRootLayout.setPadding(px, px, px, px);
        }
    }

    private void startUpdates() {
        mHandler.removeCallbacks(mUpdateRunnable);
        mHandler.post(mUpdateRunnable);
    }

    private void applyPosition() {
        mLayoutParams.x = 0;
        mLayoutParams.y = 100;
        mDraggable = "draggable".equals(mPosition);
        if (mDraggable) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            mLayoutParams.gravity = Gravity.TOP | Gravity.START;
            mLayoutParams.x = prefs.getInt(PREF_KEY_X, 0);
            mLayoutParams.y = prefs.getInt(PREF_KEY_Y, 100);
            return;
        }

        final WindowManager.LayoutParams lp = mLayoutParams;
        switch (mPosition) {
            case "top_left":
                lp.gravity = Gravity.TOP | Gravity.START;
                break;
            case "top_center":
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                break;
            case "top_right":
                lp.gravity = Gravity.TOP | Gravity.END;
                break;
            case "bottom_left":
                lp.gravity = Gravity.BOTTOM | Gravity.START;
                break;
            case "bottom_center":
                lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                break;
            case "bottom_right":
                lp.gravity = Gravity.BOTTOM | Gravity.END;
                break;
            default:
                lp.gravity = Gravity.TOP | Gravity.START;
                break;
        }
    }

    private void openOverlaySettings() {
        try {
            Intent intent = new Intent(mContext, GameBarSettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        } catch (Exception e) {
            // Exception ignored
        }
    }

    private static int dpToPx(Context context, int dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * scale);
    }
}
