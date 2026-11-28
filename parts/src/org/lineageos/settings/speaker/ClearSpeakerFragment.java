/*
 * Copyright (C) 2023 Paranoid Android
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

package org.lineageos.settings.speaker;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

import java.io.IOException;

public class ClearSpeakerFragment extends SettingsBasePreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "ClearSpeakerFragment";
    private static final String PREF_CLEAR_SPEAKER = "clear_speaker_pref";
    private static final int PLAY_DURATION_MS = 30000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mStopPlayback = this::stopPlaying;
    private AudioManager mAudioManager;
    private AudioFocusRequest mAudioFocus;
    private MediaPlayer mMediaPlayer;
    private SwitchPreferenceCompat mClearSpeakerPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.clear_speaker_settings);
        mClearSpeakerPref = findPreference(PREF_CLEAR_SPEAKER);
        mClearSpeakerPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference != mClearSpeakerPref) {
            return false;
        }
        if (!(Boolean) newValue) {
            stopPlaying();
            return true;
        }
        return startPlaying();
    }

    @Override
    public void onStop() {
        stopPlaying();
        super.onStop();
    }

    private boolean startPlaying() {
        mAudioManager = requireContext().getSystemService(AudioManager.class);
        if (mAudioManager == null || mAudioManager.getMode() != AudioManager.MODE_NORMAL) {
            return false;
        }

        AudioDeviceInfo speaker = null;
        for (AudioDeviceInfo device : mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                speaker = device;
                break;
            }
        }
        if (speaker == null) {
            return false;
        }

        requireActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        try {
            AudioFocusRequest focus = new AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change != AudioManager.AUDIOFOCUS_GAIN) {
                            stopPlaying();
                        }
                    }, mHandler)
                    .build();
            if (mAudioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return false;
            }
            mAudioFocus = focus;

            MediaPlayer player = new MediaPlayer();
            mMediaPlayer = player;
            player.setAudioAttributes(attributes);
            try (AssetFileDescriptor afd =
                    getResources().openRawResourceFd(R.raw.clear_speaker_sound)) {
                player.setDataSource(afd);
            }
            if (!player.setPreferredDevice(speaker)) {
                stopPlaying();
                return false;
            }
            player.addOnRoutingChangedListener(router -> {
                AudioDeviceInfo routedDevice = router.getRoutedDevice();
                if (mMediaPlayer == player && routedDevice != null
                        && routedDevice.getType() != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    stopPlaying();
                }
            }, mHandler);
            player.setOnErrorListener((mediaPlayer, what, extra) -> {
                if (mMediaPlayer == mediaPlayer) {
                    stopPlaying();
                }
                return true;
            });
            player.setLooping(true);
            player.prepare();
            player.start();
            mHandler.postDelayed(mStopPlayback, PLAY_DURATION_MS);
            return true;
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Failed to play the speaker cleaning tone", e);
            stopPlaying();
            return false;
        }
    }

    private void stopPlaying() {
        mHandler.removeCallbacks(mStopPlayback);
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mAudioFocus != null) {
            mAudioManager.abandonAudioFocusRequest(mAudioFocus);
            mAudioFocus = null;
        }
        mClearSpeakerPref.setChecked(false);
    }
}
