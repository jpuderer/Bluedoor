package net.jpuderer.android.bluedoor;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Arrays;

public class DeviceFragment extends Fragment implements
        AdapterView.OnItemSelectedListener, View.OnClickListener {
    private static final String TAG = "DeviceFragment";

    private LeDeviceListAdapter mLeDeviceListAdapter;

    private boolean mScanning;
    private Handler mHandler;
    private String mDefaultDeviceAddress;
    private String mDefaultDeviceName;
    private ParcelUuid mServiceUuid;
    private boolean mFirstTime = true;

    private FloatingActionButton mSelectButton;

    private ScanResultWrapper mCurrentDevice;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;

    private int mConnectionState = BluetoothLeService.STATE_DISCONNECTED;
    private int mDoorState = BluetoothLeService.DOOR_STATE_UNKNOWN;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private static final String ARG_DEFAULT_DEVICE_ADDRESS =
            "ARG_DEFAULT_DEVICE_ADDRESS";
    private static final String ARG_DEFAULT_DEVICE_NAME =
            "ARG_DEFAULT_DEVICE_NAME";
    private static final String ARG_SERVICE_UUID =
            "ARG_SERVICE_UUID";

    // Fragments need an empty default constructor
    public DeviceFragment() { }

    public static Fragment newInstance(String defaultDeviceAddress, String defaultDeviceName,
                                       ParcelUuid serviceUuid) {
        Fragment fragment = new DeviceFragment();

        Bundle args = new Bundle();
        args.putString(ARG_DEFAULT_DEVICE_ADDRESS, defaultDeviceAddress);
        args.putString(ARG_DEFAULT_DEVICE_NAME, defaultDeviceName);
        args.putParcelable(ARG_SERVICE_UUID, serviceUuid);
        fragment.setArguments(args);

        return fragment;
    }

    DeviceFragmentListener mCallback;

    // Container Activity must implement this interface
    public interface DeviceFragmentListener {
        public void onScanningStatusChange(boolean scanning);
        public void onShowScanningStatus(boolean show);
        public void onDeviceSelected(BluetoothDevice device);
        public void onUpdateView();
    }

    /**
     * This class acts as a wrapper for device scan information.  We need it, since we can't
     * create a ScanResult on our own, and we need to be able to represent the default device
     * in the list.
     */
    private class ScanResultWrapper {
        private ScanResult mScanResult;
        private BluetoothDevice mDevice;
        private String mName;
        private int mRssi;
        private int mTxPowerLevel;
        private long mTimeStampNanos;

        public ScanResultWrapper(ScanResult result) {
            mScanResult = result;
            mDevice = result.getDevice();
            mName = mDevice.getName();
            mRssi = result.getRssi();
            mTimeStampNanos = result.getTimestampNanos();
            mTxPowerLevel = result.getScanRecord().getTxPowerLevel();
        }

        public ScanResultWrapper(BluetoothDevice device, String fallbackName, int rssi,
                                 int txPoweLevel, long timestampNanos) {
            mScanResult = null;
            mDevice = device;
            mName = mDevice.getName();
            if (TextUtils.isEmpty(mName)) { mName = fallbackName; }
            mRssi = rssi;
            mTimeStampNanos = timestampNanos;
            mTxPowerLevel = txPoweLevel;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScanResultWrapper that = (ScanResultWrapper) o;
            return mDevice.equals(that.mDevice);
        }

        @Override
        public int hashCode() {
            return mDevice.hashCode();
        }

        public BluetoothDevice getDevice() {
            return mDevice;
        }

        public String getName() {
            return mName;
        }

        public String getAddress() {
            return mDevice.getAddress();
        }

        public int getRssi() {
            return mRssi;
        }

        public int getTxPowerLevel() {
            return mTxPowerLevel;
        }

        public long getTimeStampNanos() {
            return mTimeStampNanos;
        }
    }

    // Device scan callback.
    private ScanCallback mLeScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    // Sadly, we have to filter for our desired service UUID here, since
                    // it seems the offloaded filtering is broken (on the N6 at least)
                    // Some details here:
                    //    https://code.google.com/p/android/issues/detail?id=180675
                    final ScanFilter scanFilter = new ScanFilter.Builder()
                            .setServiceUuid(mServiceUuid)
                            .build();
                    if (!scanFilter.matches(result)) return;

                    ScanResultWrapper resultWrapper = new ScanResultWrapper(result);
                    mLeDeviceListAdapter.addDevice(resultWrapper);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.w(TAG, "Failed to start scan, error code: " + errorCode);
                }
            };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        mHandler = new Handler();
        mDefaultDeviceAddress = getArguments().getString(ARG_DEFAULT_DEVICE_ADDRESS, null);
        mDefaultDeviceName = getArguments().getString(ARG_DEFAULT_DEVICE_NAME, null);
        mServiceUuid = getArguments().getParcelable(ARG_SERVICE_UUID);

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothLeScanner == null) {
            Log.e(TAG, "Unable to obtain a BluetoothLeScanner.");
            return;
        }

        // Retain this fragment across configuration changes.
        setRetainInstance(true);

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.device_fragment, container, false);

        // Setup the select button
        mSelectButton = (FloatingActionButton) rootView.findViewById(R.id.select_button);
        mSelectButton.setOnClickListener(this);

        Spinner deviceSpinner = (Spinner) rootView.findViewById(R.id.device_spinner);
        deviceSpinner.setAdapter(mLeDeviceListAdapter);
        deviceSpinner.setOnItemSelectedListener(this);

        if (mFirstTime) {
            mFirstTime = false;
            scanLeDevice(true);
        }

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mCallback = (DeviceFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement DeviceFragmentListener");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        updateProgressSpinner();
        mCallback.onShowScanningStatus(true);
    }

    @Override
    public void onResume() {
        mCallback.onUpdateView();
        super.onResume();
    }

    @Override
    public void onDetach() {
        mCallback = null;
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        scanLeDevice(false);
        if (mCallback != null) {
            mCallback.onShowScanningStatus(false);
        }
        super.onDestroy();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mCurrentDevice = mLeDeviceListAdapter.getDevice(position);
        updateDeviceDetails();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        mCurrentDevice = null;
        mSelectButton.setVisibility(View.INVISIBLE);
        updateDeviceDetails();
    }

    @Override
    public void onClick(View v) {
        if (mCurrentDevice == null) return;
        mDefaultDeviceAddress = mCurrentDevice.getAddress();
        mCallback.onDeviceSelected(mCurrentDevice.getDevice());
        mLeDeviceListAdapter.notifyDataSetChanged();
        mSelectButton.setVisibility(View.INVISIBLE);
    }

    public void scanLeDevice(final boolean enable) {
        Log.d(TAG, "startBluetoothLeScan");
        if (enable) {
            mLeDeviceListAdapter.clear();

            if (!TextUtils.isEmpty(mDefaultDeviceAddress)) {
                // Add the default device as the first item in the list
                final ScanResultWrapper resultWrapper = new ScanResultWrapper(
                        mBluetoothAdapter.getRemoteDevice(mDefaultDeviceAddress),
                        "Default Device",
                        0,
                        0,
                        0);
                mLeDeviceListAdapter.addDevice(resultWrapper);
                mLeDeviceListAdapter.notifyDataSetChanged();
            }

            // Stop any existing scan first
            mBluetoothLeScanner.stopScan(mLeScanCallback);
            // Start low power BT-LE scanning
            Log.d(TAG, "mServiceUuid: " + mServiceUuid);

            // We're using a null filter here, since it seems the offloaded
            // filtering is broken (on the N6 at least).  We do the filter in
            // the results callback instead.
            //
            // Some details here:
            //    https://code.google.com/p/android/issues/detail?id=180675
            final ScanFilter scanFilter = new ScanFilter.Builder()
                    .setServiceUuid(null)
                    .build();
            final ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothLeScanner.stopScan(mLeScanCallback);
                    updateProgressSpinner();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            updateProgressSpinner();
            mBluetoothLeScanner.startScan(Arrays.asList(scanFilter),
                    scanSettings, mLeScanCallback);
        } else {
            mHandler.removeCallbacksAndMessages(null);
            mScanning = false;
            updateProgressSpinner();
            mBluetoothLeScanner.stopScan(mLeScanCallback);
        }
    }

    private void updateProgressSpinner() {
        if (mCallback != null) {
            mCallback.onScanningStatusChange(mScanning);
        }
    }

    public void updateState(int connectionState, int doorState) {
        mConnectionState = connectionState;
        mDoorState = doorState;
        updateDeviceDetails();
    }

    public void updateDeviceDetails() {
        long lastSeenTimestamp = 0;
        String name = "--";
        String status = "--";
        String rssi = "--";
        String address = "--";

        if (mCurrentDevice != null) {
            if (mCurrentDevice.getRssi() != 0)
                rssi = String.valueOf(mCurrentDevice.getRssi());

            // Is not the current default device
            if (!mCurrentDevice.getAddress().equals(mDefaultDeviceAddress)) {
                mSelectButton.setVisibility(View.VISIBLE);
                status = "Disconnected";
                lastSeenTimestamp = System.currentTimeMillis() -
                        SystemClock.elapsedRealtime() +
                        (mCurrentDevice.getTimeStampNanos() / 1000000);
                if (!TextUtils.isEmpty(mCurrentDevice.getName()))
                    name = mCurrentDevice.getName();
                if (!TextUtils.isEmpty(mCurrentDevice.getAddress()))
                    address = mCurrentDevice.getAddress();
            // Is the current default device
            } else {
                mSelectButton.setVisibility(View.INVISIBLE);
                if (mConnectionState == BluetoothLeService.STATE_CONNECTED) {
                    status = "Connected";
                    lastSeenTimestamp = System.currentTimeMillis();
                } else if (mConnectionState == BluetoothLeService.STATE_CONNECTING) {
                    status = "Connecting";
                    lastSeenTimestamp = System.currentTimeMillis();
                } else {
                    status = "Disconnected";
                }
                if (!TextUtils.isEmpty(mCurrentDevice.getName())) {
                    name = mCurrentDevice.getName();
                } else {
                    name = mDefaultDeviceName;
                }

                if (!TextUtils.isEmpty(mCurrentDevice.getAddress()))
                    address = mCurrentDevice.getAddress();
            }
        }

        final TextView statusView = (TextView) getView().findViewById(R.id.details_status);
        statusView.setText(status);

        final TextView nameView = (TextView) getView().findViewById(R.id.details_name);
        nameView.setText(name);

        final TextView addressView = (TextView) getView().findViewById(R.id.details_address);
        addressView.setText(address);

        final TextView rssiView = (TextView) getView().findViewById(R.id.details_rssi);
        rssiView.setText(rssi);

        final TextView lastSeenView = (TextView) getView().findViewById(R.id.details_last_seen);
        final CharSequence lastSeenString = DateUtils.getRelativeDateTimeString(getActivity(),
                lastSeenTimestamp,
                DateUtils.DAY_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS,
                DateUtils.FORMAT_SHOW_YEAR);
        if (lastSeenTimestamp != 0)
            lastSeenView.setText(String.valueOf(lastSeenString));
        else
            lastSeenView.setText("--");
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<ScanResultWrapper> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<ScanResultWrapper>();
            mInflator = getActivity().getLayoutInflater();
        }

        public void addDevice(ScanResultWrapper result) {
            if(!mLeDevices.contains(result)) {
                mLeDevices.add(result);
            }
        }

        public ScanResultWrapper getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                viewHolder.checkMark = (ImageView) view.findViewById(R.id.check_mark);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            ScanResultWrapper device = mLeDevices.get(i);

            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);

            if (device.getAddress().equals(mDefaultDeviceAddress)) {
                viewHolder.checkMark.setVisibility(View.VISIBLE);
            } else {
                viewHolder.checkMark.setVisibility(View.INVISIBLE);
            }
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        ImageView checkMark;
    }
}