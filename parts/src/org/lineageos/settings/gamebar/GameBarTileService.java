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

import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

public class GameBarTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState(PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("game_bar_enable", false));
    }

    @Override
    public void onClick() {
        super.onClick();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean enable = !prefs.getBoolean("game_bar_enable", false);
        if (enable && !Settings.canDrawOverlays(this)) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0,
                    GameBarSettingsActivity.overlayPermissionIntent(this),
                    PendingIntent.FLAG_IMMUTABLE));
            return;
        }

        prefs.edit().putBoolean("game_bar_enable", enable).apply();
        GameBarMonitorService.update(this);
        updateTileState(enable);
    }

    private void updateTileState(boolean enabled) {
        final Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.game_bar_tile_label));
            tile.setContentDescription(getString(R.string.game_bar_tile_description));
            tile.updateTile();
        }
    }
}
