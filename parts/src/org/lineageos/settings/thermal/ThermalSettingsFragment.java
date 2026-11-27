/**
 * Copyright (C) 2020 The LineageOS Project
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.settings.thermal;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.applications.ApplicationsState;

import org.lineageos.settings.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ThermalSettingsFragment extends Fragment
        implements ApplicationsState.Callbacks {

    private AllPackagesAdapter mAllPackagesAdapter;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private ThermalUtils mThermalUtils;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mApplicationsState = ApplicationsState.getInstance(requireActivity().getApplication());
        mActivityFilter = new ActivityFilter(requireContext().getPackageManager());
        mAllPackagesAdapter = new AllPackagesAdapter();
        mAllPackagesAdapter.setStateRestorationPolicy(
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        mThermalUtils = new ThermalUtils(requireContext());
        mSession = mApplicationsState.newSession(this, getLifecycle());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.thermal_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView apps = view.findViewById(R.id.thermal_rv_view);
        apps.setLayoutManager(new LinearLayoutManager(requireContext()));
        apps.setAdapter(mAllPackagesAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.thermal_title);
        // The profile may have changed while the per-app screen was open.
        mAllPackagesAdapter.notifyDataSetChanged();
        rebuild();
    }

    @Override
    public void onPackageListChanged() {
        mActivityFilter.updateLauncherInfoList();
        rebuild();
    }

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null) {
            mAllPackagesAdapter.setEntries(entries);
        }
    }

    @Override
    public void onLoadEntriesCompleted() {
        rebuild();
    }

    @Override
    public void onAllSizesComputed() {
    }

    @Override
    public void onLauncherInfoChanged() {
    }

    @Override
    public void onPackageIconChanged() {
    }

    @Override
    public void onPackageSizeChanged(String packageName) {
    }

    @Override
    public void onRunningStateChanged(boolean running) {
    }

    void setSearchQuery(String query) {
        mAllPackagesAdapter.setQuery(query);
    }

    private void rebuild() {
        mSession.rebuild(mActivityFilter, ApplicationsState.ALPHA_COMPARATOR);
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView profile;
        private final ImageView icon;

        private ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.app_name);
            profile = view.findViewById(R.id.app_profile);
            icon = view.findViewById(R.id.app_icon);
        }
    }

    private class AllPackagesAdapter extends RecyclerView.Adapter<ViewHolder> {

        private List<ApplicationsState.AppEntry> mAllEntries = new ArrayList<>();
        private List<ApplicationsState.AppEntry> mEntries = new ArrayList<>();
        private String mQuery = "";

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.thermal_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ApplicationsState.AppEntry entry = mEntries.get(position);

            holder.title.setText(entry.label);
            mApplicationsState.ensureIcon(entry);
            holder.icon.setImageDrawable(entry.icon);
            int packageState = mThermalUtils.getStateForPackage(entry.info.packageName);
            holder.profile.setText(ThermalUtils.getProfileNameRes(packageState));

            final int count = getItemCount();
            if (count == 1) {
                holder.itemView.setBackgroundResource(R.drawable.settingslib_round_background);
            } else if (position == 0) {
                holder.itemView.setBackgroundResource(R.drawable.settingslib_round_background_top);
            } else if (position == count - 1) {
                holder.itemView.setBackgroundResource(
                        R.drawable.settingslib_round_background_bottom);
            } else {
                holder.itemView.setBackgroundResource(
                        R.drawable.settingslib_round_background_center);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), ThermalProfileActivity.class);
                intent.putExtra(ThermalProfileFragment.EXTRA_PACKAGE_NAME,
                        entry.info.packageName);
                intent.putExtra(ThermalProfileFragment.EXTRA_APP_LABEL, entry.label);
                startActivity(intent);
            });
        }

        private void setEntries(List<ApplicationsState.AppEntry> entries) {
            mAllEntries = entries;
            applyFilter();
        }

        private void setQuery(String query) {
            mQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            applyFilter();
        }

        private void applyFilter() {
            final ArrayList<ApplicationsState.AppEntry> filtered = new ArrayList<>();
            for (ApplicationsState.AppEntry entry : mAllEntries) {
                if (TextUtils.isEmpty(mQuery) || (entry.label != null
                        && entry.label.toLowerCase(Locale.ROOT).contains(mQuery))) {
                    filtered.add(entry);
                }
            }
            mEntries = filtered;
            notifyDataSetChanged();
        }
    }

    private static class ActivityFilter implements ApplicationsState.AppFilter {

        private final PackageManager mPackageManager;
        private final Set<String> mLauncherPackages = new HashSet<>();

        private ActivityFilter(PackageManager packageManager) {
            this.mPackageManager = packageManager;

            updateLauncherInfoList();
        }

        public void updateLauncherInfoList() {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfoList = mPackageManager.queryIntentActivities(i, 0);

            synchronized (mLauncherPackages) {
                mLauncherPackages.clear();
                for (ResolveInfo ri : resolveInfoList) {
                    mLauncherPackages.add(ri.activityInfo.packageName);
                }
            }
        }

        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(ApplicationsState.AppEntry entry) {
            synchronized (mLauncherPackages) {
                return mLauncherPackages.contains(entry.info.packageName);
            }
        }
    }
}
