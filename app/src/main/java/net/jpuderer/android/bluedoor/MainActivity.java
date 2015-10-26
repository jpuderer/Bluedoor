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
import android.os.ParcelUuid;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Toast;

// TODO: Dark theme (to match application)
// TODO: Icon for application
// TODO: Add *nice* main fragment for lock/unlock/status with animations
// TODO: Style keypad: Glow/shadow, select/unselect, correct icons for buttons.
// TODO: Add preference for notifications
// TODO: Cleanup drawer styling

// TODO: Should I be using autoconnect instead of doing a lower power BT-LE scan?
// TODO: Make sure that Doze doesn't affect us
// TODO: Move strings into resources

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
    private DoorlockService mBluetoothLeService;

    private int mConnectionState = DoorlockService.STATE_DISCONNECTED;
    private int mDoorState = DoorlockService.DOOR_STATE_UNKNOWN;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((DoorlockService.LocalBinder) service).getService();
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
            if (DoorlockService.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                mConnectionState =
                        intent.getIntExtra(DoorlockService.EXTRA_CONNECTION_STATE, 0);
            } else if (DoorlockService.ACTION_DOOR_STATE_CHANGED.equals(action)) {
                mDoorState =
                        intent.getIntExtra(DoorlockService.EXTRA_DOOR_STATE, 0);
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

        Intent gattServiceIntent = new Intent(this, DoorlockService.class);
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
            Fragment fragment = DeviceFragment.newInstance(
                    getDefaultDeviceAddress(),
                    getDefaultDeviceName(),
                    new ParcelUuid(DoorlockService.BLUNO_SERVICE_UUID));
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
        setDefaultDeviceName(device.getName());
    }

    @Override
    public void onUpdateView() {
        KeypadFragment keypad = (KeypadFragment)
                getFragmentManager().findFragmentByTag(TAG_FRAGMENT_KEYPAD);
        if (keypad != null) {
            keypad.updateState(mConnectionState, mDoorState);
        }
        DeviceFragment deviceUI = (DeviceFragment)
                getFragmentManager().findFragmentByTag(TAG_FRAGMENT_DEVICE);
        if (deviceUI != null) {
            deviceUI.updateState(mConnectionState, mDoorState);
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
        return prefs.getString(DoorlockService.PREF_DEFAULT_DEVICE_ADDRESS, null);
    }

    private String getDefaultDeviceName() {
        SharedPreferences prefs = getSharedPreferences(
                getPackageName(), Context.MODE_PRIVATE);
        return prefs.getString(DoorlockService.PREF_DEFAULT_DEVICE_NAME, null);
    }

    private void setDefaultDeviceAddress(String address) {
        SharedPreferences prefs = getSharedPreferences(
                getPackageName(), Context.MODE_PRIVATE);
        prefs.edit().putString(DoorlockService.PREF_DEFAULT_DEVICE_ADDRESS, address).apply();
    }

    private void setDefaultDeviceName(String name) {
        SharedPreferences prefs = getSharedPreferences(
                getPackageName(), Context.MODE_PRIVATE);
        prefs.edit().putString(DoorlockService.PREF_DEFAULT_DEVICE_NAME, name).apply();
    }

    private void registerServiceReceiver() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DoorlockService.ACTION_CONNECTION_STATE_CHANGED);
        intentFilter.addAction(DoorlockService.ACTION_DOOR_STATE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mGattUpdateReceiver, intentFilter);
    }
}
