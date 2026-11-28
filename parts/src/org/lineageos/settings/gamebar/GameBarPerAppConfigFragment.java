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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GameBarPerAppConfigFragment extends SettingsBasePreferenceFragment {

    static final String PREF_AUTO_APPS = "game_bar_auto_apps";
    private static final String STATE_QUERY = "query";

    private EditText mSearchBar;
    private PreferenceCategory mCategory;
    private PackageManager mPm;
    private List<ApplicationInfo> mApps;
    private SharedPreferences mPrefs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        final View list = super.onCreateView(inflater, container, state);
        final Context context = requireContext();
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        mSearchBar = new EditText(context);
        mSearchBar.setHint(R.string.game_bar_search_apps);
        mSearchBar.setInputType(InputType.TYPE_CLASS_TEXT);
        mSearchBar.setSingleLine();
        mSearchBar.setBackgroundResource(R.drawable.bg_search_rounded);
        mSearchBar.setTextColor(context.getColor(R.color.app_name_text_selector));
        mSearchBar.setHintTextColor(context.getColor(R.color.app_package_text_selector));
        final int margin = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                16, getResources().getDisplayMetrics()));
        final LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMarginStart(margin);
        searchParams.setMarginEnd(margin);
        layout.addView(mSearchBar, searchParams);
        layout.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        mSearchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                populateAppList(s.toString());
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
        if (state != null) {
            mSearchBar.setText(state.getString(STATE_QUERY));
        }
        layout.setFocusableInTouchMode(true);
        layout.requestFocus();
        return layout;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSearchBar != null) {
            outState.putString(STATE_QUERY, mSearchBar.getText().toString());
        }
    }

    @Override
    public void onDestroyView() {
        mSearchBar = null;
        super.onDestroyView();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context context = requireContext();
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(context));
        mCategory = new PreferenceCategory(context);
        mCategory.setTitle(R.string.game_bar_per_app_config_title);
        getPreferenceScreen().addPreference(mCategory);

        mPm = context.getPackageManager();
        mPrefs = getPreferenceManager().getSharedPreferences();
        mApps = new ArrayList<>();
        final Set<String> packages = new HashSet<>();
        final Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : mPm.queryIntentActivities(launcher, 0)) {
            final ApplicationInfo app = info.activityInfo.applicationInfo;
            if (!context.getPackageName().equals(app.packageName) && packages.add(app.packageName)) {
                mApps.add(app);
            }
        }
        mApps.sort(new ApplicationInfo.DisplayNameComparator(mPm));
        populateAppList("");
    }

    private void populateAppList(String query) {
        final String filter = query.trim().toLowerCase(Locale.ROOT);
        final Set<String> selected = mPrefs.getStringSet(PREF_AUTO_APPS, Collections.emptySet());
        mCategory.removeAll();
        for (ApplicationInfo app : mApps) {
            final CharSequence label = app.loadLabel(mPm);
            final String packageName = app.packageName;
            if (!label.toString().toLowerCase(Locale.ROOT).contains(filter)
                    && !packageName.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }

            final SwitchPreferenceCompat pref = new SwitchPreferenceCompat(requireContext());
            pref.setKey(packageName);
            pref.setPersistent(false);
            pref.setTitle(label);
            pref.setSummary(packageName);
            pref.setChecked(selected.contains(packageName));
            pref.setIcon(app.loadIcon(mPm));
            pref.setOnPreferenceChangeListener((preference, value) -> {
                final Set<String> updated = new HashSet<>(
                        mPrefs.getStringSet(PREF_AUTO_APPS, Collections.emptySet()));
                if ((Boolean) value) {
                    updated.add(packageName);
                } else {
                    updated.remove(packageName);
                }
                mPrefs.edit().putStringSet(PREF_AUTO_APPS, updated).apply();
                return true;
            });
            mCategory.addPreference(pref);
        }
    }
}
