package net.jpuderer.android.bluedoor;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Toast;


// TODO: Update deprecated scan methods.
// TODO: Only connect to devices that contain the Bluno serial service
// TODO: Remember details for default device (in case it is out of range)
// TODO: Add detail to device fragment

// TODO: Fix logic for BT being enabled/checking
// TODO: Move the Bluetooth adapter to the scanning fragment.
// TODO: Handle graceful failure when scanning on N5?  It won't do low power scanning mode with setReportDelay.
// onBatchScanResults
// TODO: Should I be using autoconnect instead of doing a lower power BT-LE scan?
// TODO: Detect whether the door is open or closed.
// TODO: Preference to change device name
// TODO: Security of some kind?  Password/Encryption
// TODO: Preference to lock device to phone?
// TODO: Move strings into resources
// TODO: Allow action from lock screen
// TODO: Sometimes doesn't notice that the connection has gone away.  Notification still present.  Service stops?
// TODO: Add crash analytics?  Maybe?

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        DeviceFragment.DeviceFragmentListener,
        KeypadFragment.KeypadFragmentListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;

    private static final String TAG_FRAGMENT_KEYPAD = "keypad";
    private static final String TAG_FRAGMENT_DEVICE = "device";
    private static final String TAG_FRAGMENT_PREFERENCES = "preferences";

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeService mBluetoothLeService;

    private int mConnectionState = BluetoothLeService.STATE_DISCONNECTED;
    private int mDoorState = BluetoothLeService.DOOR_STATE_UNKNOWN;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            mBluetoothLeService.broadcastConnectionUpdate();
            mBluetoothLeService.broadcastDoorUpdate();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                mConnectionState =
                        intent.getIntExtra(BluetoothLeService.EXTRA_CONNECTION_STATE, 0);
            } else if (BluetoothLeService.ACTION_DOOR_STATE_CHANGED.equals(action)) {
                mDoorState =
                        intent.getIntExtra(BluetoothLeService.EXTRA_DOOR_STATE, 0);
            }
            onUpdateView();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        View refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onRefreshClicked(v); }
        });

        NavigationView mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);

        registerServiceReceiver();

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        startService(gattServiceIntent);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        if (savedInstanceState == null) {
            onNavigationItemSelected(mNavigationView.getMenu().getItem(0));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        FragmentManager fragmentManager = getFragmentManager();

        if (id == R.id.nav_keypad) {
            setTitle(R.string.nav_label_keypad);
            Fragment fragment = KeypadFragment.newInstance();
            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.replace(R.id.content_main, fragment, TAG_FRAGMENT_KEYPAD);
            ft.commit();
        } else if (id == R.id.nav_device) {
            setTitle(R.string.nav_label_bt_device);
            Fragment fragment = DeviceFragment.newInstance(getDefaultDeviceAddress());
            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.replace(R.id.content_main, fragment, TAG_FRAGMENT_DEVICE);
            ft.commit();
        } else if (id == R.id.nav_preferences) {
            setTitle(R.string.nav_label_preferences);
            Fragment fragment = LockPreferenceFragment.newInstance();
            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.replace(R.id.content_main, fragment, TAG_FRAGMENT_PREFERENCES);
            ft.commit();
        }

        item.setChecked(true);
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onScanningStatusChange(boolean scanning) {
        View progress = findViewById(R.id.toolbar_progress_bar);
        View refresh = findViewById(R.id.refresh_button);
        if (scanning) {
            progress.setVisibility(View.VISIBLE);
            refresh.setVisibility(View.GONE);
        } else {
            progress.setVisibility(View.GONE);
            refresh.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onShowScanningStatus (boolean show) {
        View scanningStatus = findViewById(R.id.scanning_status);
        if (show) {
            scanningStatus.setVisibility(View.VISIBLE);
        } else {
            scanningStatus.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onDeviceSelected(BluetoothDevice device) {
        setDefaultDeviceAddress(device.getAddress());
    }

    @Override
    public void onUpdateView() {
        KeypadFragment keypad = (KeypadFragment)
                getFragmentManager().findFragmentByTag(TAG_FRAGMENT_KEYPAD);
        if (keypad != null) {
            keypad.updateConnectionState(mConnectionState);
            keypad.updateDoorState(mDoorState);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    public void onSendCommand(byte b) {
        mBluetoothLeService.sendSerial(b);
    }

    public void onRefreshClicked(View view) {
        DeviceFragment deviceFragment = (DeviceFragment)
                getFragmentManager().findFragmentByTag(TAG_FRAGMENT_DEVICE);
        if (deviceFragment != null) {
            deviceFragment.scanLeDevice(true);
        }
    }

    private String getDefaultDeviceAddress() {
        SharedPreferences prefs = getSharedPreferences(
                getPackageName(), Context.MODE_PRIVATE);
        return prefs.getString(BluetoothLeService.PREF_DEFAULT_DEVICE_ADDRESS, null);
    }

    private void setDefaultDeviceAddress(String address) {
        SharedPreferences prefs = getSharedPreferences(
                getPackageName(), Context.MODE_PRIVATE);
        prefs.edit().putString(BluetoothLeService.PREF_DEFAULT_DEVICE_ADDRESS, address).apply();
    }

    private void registerServiceReceiver() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_CONNECTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothLeService.ACTION_DOOR_STATE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mGattUpdateReceiver, intentFilter);
    }
}
