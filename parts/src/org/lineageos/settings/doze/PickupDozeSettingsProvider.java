/* SPDX-License-Identifier: Apache-2.0 */

package org.lineageos.settings.doze;

import android.content.Context;
import android.provider.Settings;

import com.android.settingslib.drawer.EntriesProvider;
import com.android.settingslib.drawer.EntryController;
import com.android.settingslib.drawer.ProviderSwitch;

import org.lineageos.settings.R;

import java.util.List;

public class PickupDozeSettingsProvider extends EntriesProvider {
    @Override
    protected List<? extends EntryController> createEntryControllers() {
        return List.of(new PickupSwitch(getContext()));
    }

    private static class PickupSwitch extends EntryController implements ProviderSwitch {
        private final Context mContext;

        PickupSwitch(Context context) {
            mContext = context;
        }

        @Override
        public String getKey() {
            return "alioth_pickup_doze";
        }

        @Override
        protected MetaData getMetaData() {
            return new MetaData("com.android.settings.category.ia.display")
                    .setTitle(R.string.pickup_doze_title)
                    .setSummary(R.string.pickup_doze_summary);
        }

        @Override
        public boolean isSwitchChecked() {
            return Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.DOZE_PICK_UP_GESTURE, 1) != 0;
        }

        @Override
        public boolean onSwitchCheckedChanged(boolean checked) {
            return Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.DOZE_PICK_UP_GESTURE, checked ? 1 : 0);
        }

        @Override
        public String getSwitchErrorMessage(boolean attemptedChecked) {
            return mContext.getString(R.string.pickup_doze_update_failed);
        }
    }
}
