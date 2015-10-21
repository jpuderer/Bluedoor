/*
 * Copyright (C) 2013 The Android Open Source Project
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

package net.jpuderer.android.bluedoor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    private int mDoorState = DOOR_STATE_UNKNOWN;
    private BluetoothGattService mGattBlunoService;
    private BluetoothGattService mGattDeviceInfoService;
    private SharedPreferences mSharedPreferences;

    private final IBinder mBinder = new LocalBinder();

    public final static String ACTION_CONNECTION_STATE_CHANGED =
            "net.jpuderer.android.bluedoor.ACTION_CONNECTION_STATE_CHANGED";
    public final static String EXTRA_CONNECTION_STATE =
            "net.jpuderer.android.bluedoor.EXTRA_CONNECTION_STATE";
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    public final static String ACTION_DOOR_STATE_CHANGED =
            "net.jpuderer.android.bluedoor.ACTION_DOOR_STATE_CHANGED";
    public final static String EXTRA_DOOR_STATE =
            "net.jpuderer.android.bluedoor.EXTRA_CONNECTION_STATE";
    public static final int DOOR_STATE_UNKNOWN = 0;
    public static final int DOOR_STATE_LOCKED = 1;
    public static final int DOOR_STATE_UNLOCKED = 2;

    public final static String ACTION_LOCK =
            "net.jpuderer.android.bluedoor.ACTION_LOCK";
    public final static String ACTION_UNLOCK =
            "net.jpuderer.android.bluedoor.ACTION_UNLOCK";

    public final static UUID HID_SERVICE_UUID =
            UUID.fromString("00001812-0000-1000-8000-00805f9b34fb");
    public final static UUID BLUNO_SERVICE_UUID =
            UUID.fromString("0000dfb0-0000-1000-8000-00805f9b34fb");
    public final static UUID DEVICE_INFORMATION_SERVICE_UUID =
            UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public final static UUID SERIAL_PORT_CHARACTERISTIC_UUID =
            UUID.fromString("0000dfb1-0000-1000-8000-00805f9b34fb");
    public static final UUID AT_COMMAND_CHARACTERISTIC_UUID =
            UUID.fromString("0000dfb2-0000-1000-8000-00805f9b34fb");
    public static final UUID MODEL_NUMBER_STRING_CHARACTERISTIC_UUID =
            UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final String PREF_DEFAULT_DEVICE_ADDRESS =
            "PREF_DEFAULT_DEVICE_ADDRESS";
    public static final String PREF_DEFAULT_DEVICE_NAME =
            "PREF_DEFAULT_DEVICE_NAME";

    // Bluno serial characteristic can not receive more than 17 characters
    // at once.
    public static final int MAX_SERIAL_TX_SIZE = 17;

    // Keys and commands to send to door

    public static final byte GET_STATUS_COMMAND = 0x00;
    public static final byte KEYPAD_COMMAND_KEY_0 = 0x30;
    public static final byte KEYPAD_COMMAND_KEY_1 = 0x31;
    public static final byte KEYPAD_COMMAND_KEY_2 = 0x32;
    public static final byte KEYPAD_COMMAND_KEY_3 = 0x33;
    public static final byte KEYPAD_COMMAND_KEY_4 = 0x34;
    public static final byte KEYPAD_COMMAND_KEY_5 = 0x35;
    public static final byte KEYPAD_COMMAND_KEY_6 = 0x36;
    public static final byte KEYPAD_COMMAND_KEY_7 = 0x37;
    public static final byte KEYPAD_COMMAND_KEY_8 = 0x38;
    public static final byte KEYPAD_COMMAND_KEY_9 = 0x39;
    public static final byte KEYPAD_COMMAND_KEY_ENTER = 0x23;
    public static final byte KEYPAD_COMMAND_KEY_CANCEL = 0x2A;

    // Status bytes to receive from door
    public static final byte LOCK_STATUS_BYTE = 0x61;
    public static final byte UNLOCK_STATUS_BYTE = 0x62;
    public static final byte ERROR_STATUS_BYTE = 0x66;

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction = ACTION_CONNECTION_STATE_CHANGED;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "onConnectionStateChange: Connected");
                mConnectionState = STATE_CONNECTED;
                // Stop any active scan
                mBluetoothLeScanner.stopScan(mScanCallback);
                broadcastConnectionUpdate();
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "onConnectionStateChange: Disconnected");
                mConnectionState = STATE_DISCONNECTED;
                mGattBlunoService = null;
                mGattDeviceInfoService = null;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastConnectionUpdate();
                // Restart Bluetooth scan
                if (mBluetoothAdapter.isEnabled()) {
                    startBluetoothLeScan();
                }
            }
            updateNotification();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mGattBlunoService = mBluetoothGatt.getService(BLUNO_SERVICE_UUID);
                mGattDeviceInfoService = mBluetoothGatt.getService(DEVICE_INFORMATION_SERVICE_UUID);

                BluetoothGattCharacteristic characteristic =
                        mGattBlunoService.getCharacteristic(SERIAL_PORT_CHARACTERISTIC_UUID);
                mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                sendSerial(GET_STATUS_COMMAND);
            } else {
                Log.w(TAG, "onServicesDiscovered status: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(SERIAL_PORT_CHARACTERISTIC_UUID)) {
                    onReceiveSerial(characteristic.getValue());
                }
            } else {
                Log.w(TAG, "onCharacteristicRead status: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (!characteristic.getUuid().equals(SERIAL_PORT_CHARACTERISTIC_UUID))
                return;
            onReceiveSerial(characteristic.getValue());
        }
    };

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanCallback");
            // If we found the default device, connect to it
            if (result.getDevice().getAddress().equals(getDefaultDeviceAddress())) {
                Log.d(TAG, "onScanCallback: Found default device");
                connect(result.getDevice().getAddress());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "Failed to start scan, error code: " + errorCode);
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (PREF_DEFAULT_DEVICE_ADDRESS.equals(key)) {
                final String address = getDefaultDeviceAddress();
                if (!TextUtils.isEmpty(address)) {
                    connect(address);
                }
            }
        }
    };

    public void broadcastConnectionUpdate() {
        final Intent intent = new Intent(ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(EXTRA_CONNECTION_STATE, mConnectionState);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void broadcastDoorUpdate() {
        final Intent intent = new Intent(ACTION_DOOR_STATE_CHANGED);
        intent.putExtra(EXTRA_DOOR_STATE, mDoorState);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        mSharedPreferences = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        final boolean initialized = initialize();

        // If Bluetooth is enabled *and* we have a default device configured
        // start scanning in low power mode
        final boolean hasDefaultDevice = !TextUtils.isEmpty(getDefaultDeviceAddress());
        if (!initialized || !hasDefaultDevice) {
            // Nothing to do, so just stop ourselves until something changes
            stopSelf();
            return START_NOT_STICKY;
        }

        // The notification fires an intent to lock/unlock the door
        if (intent != null && mConnectionState == STATE_CONNECTED) {
            if (ACTION_UNLOCK.equals(intent.getAction())) {
                unlockDoor();
            } else if (ACTION_UNLOCK.equals(intent.getAction())) {
                lockDoor();
            }
        }

        startBluetoothLeScan();
        return START_STICKY;
    }

    private void startBluetoothLeScan() {
        Log.d(TAG, "startBluetoothLeScan");
        // Stop any existing scan first
        mBluetoothLeScanner.stopScan(mScanCallback);
        // Start low power BT-LE scanning
        final ScanFilter scanFilter = new ScanFilter.Builder()
                .setDeviceAddress(getDefaultDeviceAddress())
                .build();
        final ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();
        mBluetoothLeScanner.startScan(Arrays.asList(scanFilter),
                scanSettings, mScanCallback);
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "BluetoothAdapter is not enabled.");
            return false;
        }

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothLeScanner == null) {
            Log.e(TAG, "Unable to obtain a BluetoothLeScanner.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        Log.d(TAG, "connect");
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                broadcastConnectionUpdate();
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        // Disconnect before creating a new connection.  Otherwise, the device seems to remain
        // connected, but we no longer have a handle to it.
        if (mConnectionState != STATE_DISCONNECTED) {
            mBluetoothGatt.disconnect();
        }

        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        broadcastConnectionUpdate();
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        Log.d(TAG, "disconnect");
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    public void sendSerial(byte b) {
        final byte[] data = {b};
        sendSerial(data);
    }

    public void sendSerial(byte[] data) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if ((mGattBlunoService == null) || (mGattDeviceInfoService == null)) {
            Log.w(TAG, "Bluetooth service has not been discovered");
            return;
        }
        if (data.length > MAX_SERIAL_TX_SIZE) {
            Log.w(TAG, "Maximum data size exceeded.  Cannot send more than " +
                    MAX_SERIAL_TX_SIZE + " bytes");
            return;
        }
        BluetoothGattCharacteristic characteristic =
                mGattBlunoService.getCharacteristic(SERIAL_PORT_CHARACTERISTIC_UUID);
        characteristic.setValue(data);
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    private void onReceiveSerial(byte[] data) {
        String message = "???";
        // The most recent command byte in the buffer is the only one we're interested in
        for (int i = (data.length - 1); i >= 0; i--) {
            final byte b = data[i];
            Log.d(TAG, String.format("byte: 0x%x", b));
            switch (data[i]) {
                case LOCK_STATUS_BYTE:
                    mDoorState = DOOR_STATE_LOCKED;
                    broadcastDoorUpdate();
                    message = "Door locked";
                    break;
                case UNLOCK_STATUS_BYTE:
                    mDoorState = DOOR_STATE_UNLOCKED;
                    broadcastDoorUpdate();
                    message = "Door unlocked";
                    break;
                case ERROR_STATUS_BYTE:
                    Log.w(TAG, "Error status received from lock.");
                    break;
                case ((byte) 0xFF):
                    // Command start byte, ignore
                    break;
                default:
                    Log.w(TAG, String.format("Unknown command byte received from lock: 0x%x", data[i]));
                    break;
            }
        }
    }

    public void lockDoor() {
        // FIXME: There's a special command that we can use here.
    }

    public void unlockDoor() {
        byte[] command = {
                KEYPAD_COMMAND_KEY_1,
                KEYPAD_COMMAND_KEY_2,
                KEYPAD_COMMAND_KEY_3,
                KEYPAD_COMMAND_KEY_4,
                KEYPAD_COMMAND_KEY_ENTER};
        sendSerial(command);
    }

    private String getDefaultDeviceAddress() {
        return mSharedPreferences.getString(
                PREF_DEFAULT_DEVICE_ADDRESS, null);
    }

    private void setDefaultDeviceAddress(String address) {
        mSharedPreferences.edit().putString(
                PREF_DEFAULT_DEVICE_ADDRESS, address).commit();
    }

    private void updateNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (mConnectionState != STATE_CONNECTED) {
            notificationManager.cancelAll();
            return;
        }

        Intent intent = new Intent(this, BluetoothLeService.class);
        intent.setAction(ACTION_UNLOCK);
        PendingIntent pIntent = PendingIntent.getService(this,
                (int) System.currentTimeMillis(), intent, 0);

        // build notification
        // the addAction re-use the same intent to keep the example short
        Notification n  = new Notification.Builder(this)
                .setContentTitle("Connected to Bluetooth device")
                .setContentText("Press to unlock")
                .setSmallIcon(android.R.drawable.star_on)
                .setContentIntent(pIntent)
                .setOngoing(true)
                .build();

        notificationManager.notify(0, n);
    }
}
