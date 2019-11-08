/*
 * Copyright 2017 The Android Open Source Project
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

package com.google.sample.oboe.hellooboe;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import androidx.core.view.MotionEventCompat;

import android.os.IBinder;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tcomtest.CallService;
import com.example.tcomtest.TComConnection;
import com.example.tcomtest.TComManager;
import com.example.tcomtest.TComService;
import com.google.sample.audio_device.AudioDeviceListEntry;
import com.google.sample.audio_device.AudioDeviceSpinner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
    private static final long UPDATE_LATENCY_EVERY_MILLIS = 1000;
    private static final Integer[] CHANNEL_COUNT_OPTIONS = {1, 2, 3, 4, 5, 6, 7, 8};
    // Default to Stereo (OPTIONS is zero-based array so index 1 = 2 channels)
    private static final int CHANNEL_COUNT_DEFAULT_OPTION_INDEX = 1;
    private static final int[] BUFFER_SIZE_OPTIONS = {0, 1, 2, 4, 8};
    private static final String[] AUDIO_API_OPTIONS = {"Unspecified", "OpenSL ES", "AAudio"};
    // Default all other spinners to the first option on the list
    private static final int SPINNER_DEFAULT_OPTION_INDEX = 0;

    private Spinner mAudioApiSpinner;
    private AudioDeviceSpinner mPlaybackDeviceSpinner;
    private Spinner mChannelCountSpinner;
    private Spinner mBufferSizeSpinner;
    private TextView mLatencyText;
    private Timer mLatencyUpdater;

    /*
     * Hook to user control to start / stop audio playback:
     *    touch-down: start, and keeps on playing
     *    touch-up: stop.
     * simply pass the events to native side.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);
        switch (action) {
            case (MotionEvent.ACTION_DOWN):
                PlaybackEngine.setToneOn(true);
                break;
            case (MotionEvent.ACTION_UP):
                PlaybackEngine.setToneOn(false);
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mLatencyText = findViewById(R.id.latencyText);
        setupAudioApiSpinner();
        setupPlaybackDeviceSpinner();
        setupChannelCountSpinner();
        setupBufferSizeSpinner();

        init();
    }
    /*
    * Creating engine in onResume() and destroying in onPause() so the stream retains exclusive
    * mode only while in focus. This allows other apps to reclaim exclusive stream mode.
    */
    @Override
    protected void onResume() {
        super.onResume();
        PlaybackEngine.create(this);
        setupLatencyUpdater();
        // Return the spinner states to their default value
        mChannelCountSpinner.setSelection(CHANNEL_COUNT_DEFAULT_OPTION_INDEX);
        mPlaybackDeviceSpinner.setSelection(SPINNER_DEFAULT_OPTION_INDEX);
        mBufferSizeSpinner.setSelection(SPINNER_DEFAULT_OPTION_INDEX);
        mAudioApiSpinner.setSelection(SPINNER_DEFAULT_OPTION_INDEX);
    }

    @Override
    protected void onPause() {
       if (mLatencyUpdater != null) mLatencyUpdater.cancel();
       PlaybackEngine.delete();
       super.onPause();
    }

    private void setupChannelCountSpinner() {
        mChannelCountSpinner = findViewById(R.id.channelCountSpinner);

        ArrayAdapter<Integer> channelCountAdapter = new ArrayAdapter<Integer>(this, R.layout.channel_counts_spinner, CHANNEL_COUNT_OPTIONS);
        mChannelCountSpinner.setAdapter(channelCountAdapter);
        mChannelCountSpinner.setSelection(CHANNEL_COUNT_DEFAULT_OPTION_INDEX);

        mChannelCountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                PlaybackEngine.setChannelCount(CHANNEL_COUNT_OPTIONS[mChannelCountSpinner.getSelectedItemPosition()]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    private void setupBufferSizeSpinner() {
        mBufferSizeSpinner = findViewById(R.id.bufferSizeSpinner);
        mBufferSizeSpinner.setAdapter(new SimpleAdapter(
                this,
                createBufferSizeOptionsList(), // list of buffer size options
                R.layout.buffer_sizes_spinner, // the xml layout
                new String[]{getString(R.string.buffer_size_description_key)}, // field to display
                new int[]{R.id.bufferSizeOption} // View to show field in
        ));

        mBufferSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                PlaybackEngine.setBufferSizeInBursts(getBufferSizeInBursts());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    private void setupPlaybackDeviceSpinner() {
        mPlaybackDeviceSpinner = findViewById(R.id.playbackDevicesSpinner);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mPlaybackDeviceSpinner.setDirectionType(AudioManager.GET_DEVICES_OUTPUTS);
            mPlaybackDeviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    PlaybackEngine.setAudioDeviceId(getPlaybackDeviceId());
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });
        }
    }

    private void setupAudioApiSpinner() {
        mAudioApiSpinner = findViewById(R.id.audioApiSpinner);
        mAudioApiSpinner.setAdapter(new SimpleAdapter(
                this,
                createAudioApisOptionsList(),
                R.layout.audio_apis_spinner, // the xml layout
                new String[]{getString(R.string.audio_api_description_key)}, // field to display
                new int[]{R.id.audioApiOption} // View to show field in
        ));

        mAudioApiSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                PlaybackEngine.setAudioApi(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    private int getPlaybackDeviceId() {
        return ((AudioDeviceListEntry) mPlaybackDeviceSpinner.getSelectedItem()).getId();
    }

    private int getBufferSizeInBursts() {
        @SuppressWarnings("unchecked")
        HashMap<String, String> selectedOption = (HashMap<String, String>)
                mBufferSizeSpinner.getSelectedItem();

        String valueKey = getString(R.string.buffer_size_value_key);

        // parseInt will throw a NumberFormatException if the string doesn't contain a valid integer
        // representation. We don't need to worry about this because the values are derived from
        // the BUFFER_SIZE_OPTIONS int array.
        return Integer.parseInt(selectedOption.get(valueKey));
    }

    private void setupLatencyUpdater() {
        //Update the latency every 1s
        TimerTask latencyUpdateTask = new TimerTask() {
            @Override
            public void run() {
                final String latencyStr;
                if (PlaybackEngine.isLatencyDetectionSupported()) {
                    double latency = PlaybackEngine.getCurrentOutputLatencyMillis();
                    if (latency >= 0) {
                        latencyStr = String.format(Locale.getDefault(), "%.2fms", latency);
                    } else {
                        latencyStr = "Unknown";
                    }
                } else {
                    latencyStr = getString(R.string.only_supported_on_api_26);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mLatencyText.setText(getString(R.string.latency, latencyStr));
                    }
                });
            }
        };
        mLatencyUpdater = new Timer();
        mLatencyUpdater.schedule(latencyUpdateTask, 0, UPDATE_LATENCY_EVERY_MILLIS);
    }

    /**
     * Creates a list of buffer size options which can be used to populate a SimpleAdapter.
     * Each option has a description and a value. The description is always equal to the value,
     * except when the value is zero as this indicates that the buffer size should be set
     * automatically by the audio engine
     *
     * @return list of buffer size options
     */
    private List<HashMap<String, String>> createBufferSizeOptionsList() {

        ArrayList<HashMap<String, String>> bufferSizeOptions = new ArrayList<>();

        for (int i : BUFFER_SIZE_OPTIONS) {
            HashMap<String, String> option = new HashMap<>();
            String strValue = String.valueOf(i);
            String description = (i == 0) ? getString(R.string.automatic) : strValue;
            option.put(getString(R.string.buffer_size_description_key), description);
            option.put(getString(R.string.buffer_size_value_key), strValue);

            bufferSizeOptions.add(option);
        }

        return bufferSizeOptions;
    }

    private List<HashMap<String, String>> createAudioApisOptionsList() {

        ArrayList<HashMap<String, String>> audioApiOptions = new ArrayList<>();

        for (int i = 0; i < AUDIO_API_OPTIONS.length; i++) {
            HashMap<String, String> option = new HashMap<>();
            option.put(getString(R.string.buffer_size_description_key), AUDIO_API_OPTIONS[i]);
            option.put(getString(R.string.buffer_size_value_key), String.valueOf(i));
            audioApiOptions.add(option);
        }
        return audioApiOptions;
    }






    ///////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, CallService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeConnection();
        unbindService(serviceConnection);
    }

    class CallServiceConnection implements ServiceConnection {
        private CallService callService = null;
        public void onServiceConnected(ComponentName name, IBinder binder) {
            CallService.CallServiceBinder callSrvBinder = (CallService.CallServiceBinder)binder;
            CallService service = callSrvBinder.getCallService();
            service.setConnectionListener(con -> { addConnection(con); return null; } );
            this.callService = service;
        }

        public void onServiceDisconnected(ComponentName name) {
            callService.setConnectionListener((con)-> null);
        }
    }

    private boolean hasPermissions() {
        for (String permission : requiredPermissions) {
            if (checkSelfPermission(permission) !=PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    private void closeConnection() {
        if (connection != null) {
            if (!connection.isClosed()) {
                connection.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
            }
            connection.setListener((conn)->null);
            connection.destroy();
            connection = null;
        }

        state_label.setText("state: no call");
    }


    private void addConnection(TComConnection newConnection) {
        newConnection.setListener(it -> {
            String state = Connection.stateToString(it);
            state_label.setText(String.format( "state: %s", state));
            return null;
        });

        this.connection = newConnection;
        String state = Connection.stateToString(newConnection.getState());
        state_label.setText(String.format( "state: %s", state));
    }


    private CallServiceConnection serviceConnection;
    private TComConnection connection;
    private TComManager tcomManager;
    private String[] requiredPermissions = {android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.READ_CALL_LOG};
    private Button answer_btn;
    private Button drop_btn;
    private Button incoming_btn;
    private Button outgoing_btn;
    private TextView state_label;

    private void init() {
        answer_btn = findViewById(R.id.answer_btn);
        drop_btn = findViewById(R.id.drop_btn);
        incoming_btn = findViewById(R.id.incoming_btn);
        outgoing_btn = findViewById(R.id.outgoing_btn);
        state_label = findViewById(R.id.state_label);
        tcomManager = new TComManager(getApplicationContext());
        serviceConnection = new CallServiceConnection();


        drop_btn.setOnClickListener(v -> closeConnection());

        answer_btn.setOnClickListener( v -> {
            if (connection != null) {
                connection.setActive();
            } else {
                Toast.makeText(getApplicationContext(), "there is no call", Toast.LENGTH_SHORT).show();
            }
        });

        incoming_btn.setOnClickListener( v -> {
            if (!hasPermissions()) {
                requestPermissions(requiredPermissions, 22);
                Toast.makeText(getApplicationContext(), "don't have permissions", Toast.LENGTH_SHORT).show();
                return;
            }

            if (connection != null) {
                Toast.makeText(getApplicationContext(), "drop the connection first", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                if (tcomManager.registerAccount()) {
                    tcomManager.addIncomingCall();
                } else {
                    Toast.makeText(getApplicationContext(), "account isn't registered", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        outgoing_btn.setOnClickListener( v-> {
            if (!hasPermissions()) {
                requestPermissions(requiredPermissions, 22);
                Toast.makeText(getApplicationContext(), "don't have permissions", Toast.LENGTH_SHORT).show();
                return;
            }

            if (connection != null) {
                Toast.makeText(getApplicationContext(), "drop the connection first", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                if (tcomManager.registerAccount()) {
                    tcomManager.addOutgoingCall();
                } else {
                    Toast.makeText(getApplicationContext(), "account isn't registered", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
