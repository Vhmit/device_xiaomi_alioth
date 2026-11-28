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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.TwoStatePreference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class GameBarFragment extends SettingsBasePreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.game_bar_preferences, rootKey);

        findPreference("game_bar_per_app_config").setOnPreferenceClickListener(pref -> {
            startActivity(new Intent(requireContext(), GameBarPerAppConfigActivity.class));
            return true;
        });

        final GameDataExport capture = GameDataExport.getInstance();
        findPreference("game_bar_capture_start").setOnPreferenceClickListener(pref -> {
            capture.startCapture();
            showMessage(R.string.game_bar_capture_started);
            return true;
        });
        findPreference("game_bar_capture_stop").setOnPreferenceClickListener(pref -> {
            capture.stopCapture();
            showMessage(R.string.game_bar_capture_stopped);
            return true;
        });
        findPreference("game_bar_capture_export").setOnPreferenceClickListener(pref -> {
            final Context context = requireContext().getApplicationContext();
            capture.exportDataToCsv(context,
                    result -> Toast.makeText(context, result, Toast.LENGTH_SHORT).show());
            return true;
        });

        findPreference("game_bar_auto_enable").setOnPreferenceChangeListener((pref, value) -> {
            if ((Boolean) value && !Settings.canDrawOverlays(requireContext())) {
                startActivity(GameBarSettingsActivity.overlayPermissionIntent(requireContext()));
                return false;
            }
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        final TwoStatePreference masterSwitch = findPreference("game_bar_enable");
        final TwoStatePreference autoSwitch = findPreference("game_bar_auto_enable");
        masterSwitch.setChecked(prefs.getBoolean("game_bar_enable", false));
        autoSwitch.setChecked(prefs.getBoolean("game_bar_auto_enable", false));
        prefs.registerOnSharedPreferenceChangeListener(this);
        GameBarMonitorService.update(requireContext());
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if ("game_bar_enable".equals(key)) {
            if (prefs.getBoolean(key, false) && !Settings.canDrawOverlays(requireContext())) {
                final TwoStatePreference masterSwitch = findPreference(key);
                masterSwitch.setChecked(false);
                startActivity(GameBarSettingsActivity.overlayPermissionIntent(requireContext()));
            }
            GameBarMonitorService.update(requireContext());
        } else if ("game_bar_auto_enable".equals(key)) {
            GameBarMonitorService.update(requireContext());
        } else if (key != null && key.startsWith("game_bar_")
                && !"game_bar_x".equals(key) && !"game_bar_y".equals(key)) {
            GameBar.getInstance(requireContext()).applyPreferences();
        }
    }

    private void showMessage(int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
