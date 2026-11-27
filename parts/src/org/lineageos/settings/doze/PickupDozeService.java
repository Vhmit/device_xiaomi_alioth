/* SPDX-License-Identifier: Apache-2.0 */

package org.lineageos.settings.doze;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.hardware.display.AmbientDisplayConfiguration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

public class PickupDozeService extends Service {
    private static final long SCREEN_OFF_DEBOUNCE_MS = 1000;
    private static final long MIN_PULSE_INTERVAL_MS = 5000;

    private AmbientDisplayConfiguration mAmbientConfig;
    private KeyguardManager mKeyguardManager;
    private PowerManager mPowerManager;
    private SensorManager mSensorManager;
    private Sensor mPickupSensor;
    private boolean mListening;
    private long mNextPulseTime;

    private final TriggerEventListener mPickupListener = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
            mListening = false;
            final long now = SystemClock.elapsedRealtime();
            if (shouldListen() && mKeyguardManager.isKeyguardLocked() && now >= mNextPulseTime) {
                mNextPulseTime = now + MIN_PULSE_INTERVAL_MS;
                sendBroadcast(new Intent("com.android.systemui.doze.pulse")
                        .setPackage("com.android.systemui"));
            }
            updateListening();
        }
    };

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mNextPulseTime = SystemClock.elapsedRealtime() + SCREEN_OFF_DEBOUNCE_MS;
            }
            updateListening();
        }
    };

    private final ContentObserver mSettingsObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateListening();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mAmbientConfig = new AmbientDisplayConfiguration(this);
        mKeyguardManager = getSystemService(KeyguardManager.class);
        mPowerManager = getSystemService(PowerManager.class);
        mSensorManager = getSystemService(SensorManager.class);
        mPickupSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE, true);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        registerReceiver(mScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        for (String setting : new String[] {Settings.Secure.DOZE_PICK_UP_GESTURE,
                Settings.Secure.DOZE_ENABLED, Settings.Secure.DOZE_ALWAYS_ON,
                Settings.Secure.SUPPRESS_DOZE}) {
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(setting),
                    false, mSettingsObserver);
        }
        updateListening();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mScreenReceiver);
        getContentResolver().unregisterContentObserver(mSettingsObserver);
        if (mListening) {
            mSensorManager.cancelTriggerSensor(mPickupListener, mPickupSensor);
            mListening = false;
        }
        super.onDestroy();
    }

    private boolean shouldListen() {
        return mPickupSensor != null && !mPowerManager.isInteractive()
                && !mPowerManager.isPowerSaveMode() && mAmbientConfig.enabled(getUserId())
                && !mAmbientConfig.alwaysOnEnabled(getUserId())
                && !mAmbientConfig.dozeSuppressed(getUserId())
                && Settings.Secure.getInt(getContentResolver(),
                        Settings.Secure.DOZE_PICK_UP_GESTURE, 1) != 0;
    }

    private void updateListening() {
        if (shouldListen()) {
            if (!mListening) {
                mListening = mSensorManager.requestTriggerSensor(mPickupListener, mPickupSensor);
            }
        } else if (mListening) {
            mSensorManager.cancelTriggerSensor(mPickupListener, mPickupSensor);
            mListening = false;
        }
    }
}
