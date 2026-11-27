/*
 * Copyright (C) 2020 The LineageOS Project
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

package org.lineageos.settings.thermal;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarAppCompatActivity;
import com.android.settingslib.widget.ExpressiveDesignEnabledProvider;

import org.lineageos.settings.R;

public class ThermalActivity extends CollapsingToolbarAppCompatActivity
        implements ExpressiveDesignEnabledProvider {

    private static final String TAG_THERMAL = "thermal";
    private static final String KEY_SEARCH_QUERY = "thermal_search_query";

    private MenuItem mSearchItem;
    private SearchView mSearchView;
    private boolean mSearchMode;
    private String mRestoredSearchQuery;
    private int mExpandedHeaderHeight;

    private final OnBackPressedCallback mSearchBackCallback =
            new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    closeSearch();
                }
            };

    @Override
    public boolean isExpressiveDesignEnabled() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    new ThermalSettingsFragment(), TAG_THERMAL).commitNow();
        } else {
            mRestoredSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
        }

        getOnBackPressedDispatcher().addCallback(this, mSearchBackCallback);
        setActionButtonEnabled(false);

        final Toolbar toolbar = getToolbar();
        if (toolbar != null) {
            toolbar.setCollapseIcon(com.android.settingslib.collapsingtoolbar.R.drawable
                    .settingslib_expressive_icon_back);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSearchMode && mSearchView != null) {
            outState.putString(KEY_SEARCH_QUERY, mSearchView.getQuery().toString());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.thermal_search_menu, menu);
        mSearchItem = menu.findItem(R.id.action_thermal_search);
        final int onSurface = getColor(R.color.settingslib_materialColorOnSurface);
        final int onSurfaceVariant = getColor(R.color.settingslib_materialColorOnSurfaceVariant);
        final int primary = getColor(R.color.settingslib_materialColorPrimary);
        mSearchItem.setIconTintList(ColorStateList.valueOf(onSurfaceVariant));

        mSearchView = (SearchView) mSearchItem.getActionView();
        mSearchView.setQueryHint(getString(R.string.thermal_search_hint));
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                final InputMethodManager inputMethodManager =
                        getSystemService(InputMethodManager.class);
                if (inputMethodManager != null) {
                    inputMethodManager.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (mSearchMode) {
                    setSearchQuery(newText);
                }
                return true;
            }
        });
        styleSearchView(onSurface, onSurfaceVariant, primary);

        mSearchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchMode = true;
                mSearchBackCallback.setEnabled(true);
                setHeaderCompact(true);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchMode = false;
                mSearchBackCallback.setEnabled(false);
                setHeaderCompact(false);
                setSearchQuery("");
                return true;
            }
        });

        if (mRestoredSearchQuery != null) {
            final String query = mRestoredSearchQuery;
            mRestoredSearchQuery = null;
            getWindow().getDecorView().post(() -> {
                mSearchItem.expandActionView();
                mSearchView.setQuery(query, false);
            });
        }
        return true;
    }

    // Remove the stock SearchView decorations and use the Expressive theme colors.
    private void styleSearchView(int onSurface, int onSurfaceVariant, int primary) {
        final EditText srcText = mSearchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (srcText != null) {
            srcText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            srcText.setPaddingRelative(0, srcText.getPaddingTop(), srcText.getPaddingEnd(),
                    srcText.getPaddingBottom());
            srcText.setTextColor(onSurface);
            srcText.setHintTextColor(onSurfaceVariant);
            final GradientDrawable cursor = new GradientDrawable();
            cursor.setSize(dp(2), 0);
            cursor.setColor(primary);
            srcText.setTextCursorDrawable(cursor);
        }
        final View plate = mSearchView.findViewById(androidx.appcompat.R.id.search_plate);
        if (plate != null) {
            plate.setBackground(null);
        }
        final View editFrame = mSearchView.findViewById(androidx.appcompat.R.id.search_edit_frame);
        if (editFrame != null) {
            editFrame.setPaddingRelative(0, editFrame.getPaddingTop(), editFrame.getPaddingEnd(),
                    editFrame.getPaddingBottom());
            if (editFrame.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                final ViewGroup.MarginLayoutParams lp =
                        (ViewGroup.MarginLayoutParams) editFrame.getLayoutParams();
                lp.setMarginStart(0);
                editFrame.setLayoutParams(lp);
            }
        }
        final ImageView magIcon = mSearchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
        if (magIcon != null) {
            magIcon.setVisibility(View.GONE);
        }
        final ImageView closeButton =
                mSearchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        if (closeButton != null) {
            closeButton.setColorFilter(onSurfaceVariant);
        }
    }

    // Keep the app list directly below the search field while search is expanded.
    private void setHeaderCompact(boolean compact) {
        final View collapsing = findViewById(
                com.android.settingslib.collapsingtoolbar.R.id.collapsing_toolbar);
        if (collapsing == null) {
            return;
        }
        final ViewGroup.LayoutParams lp = collapsing.getLayoutParams();
        if (compact) {
            if (mExpandedHeaderHeight <= 0 && lp.height > 0) {
                mExpandedHeaderHeight = lp.height;
            }
            final Toolbar toolbar = getToolbar();
            lp.height = toolbar != null && toolbar.getHeight() > 0
                    ? toolbar.getHeight() : dp(56);
        } else {
            lp.height = mExpandedHeaderHeight > 0
                    ? mExpandedHeaderHeight
                    : getResources().getDimensionPixelSize(com.android.settingslib
                            .collapsingtoolbar.R.dimen.settingslib_toolbar_layout_height);
        }
        collapsing.setLayoutParams(lp);
    }

    private boolean closeSearch() {
        return mSearchItem != null && mSearchItem.isActionViewExpanded()
                && mSearchItem.collapseActionView();
    }

    private void setSearchQuery(String query) {
        final ThermalSettingsFragment fragment = (ThermalSettingsFragment)
                getSupportFragmentManager().findFragmentByTag(TAG_THERMAL);
        if (fragment != null) {
            fragment.setSearchQuery(query);
        }
    }

    private Toolbar getToolbar() {
        return findViewById(com.android.settingslib.collapsingtoolbar.R.id.support_action_bar);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (!closeSearch()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return closeSearch() || super.onSupportNavigateUp();
    }
}
