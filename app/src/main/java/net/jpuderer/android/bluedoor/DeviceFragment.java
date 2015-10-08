package net.jpuderer.android.bluedoor;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;

public class DeviceFragment extends Fragment implements
        AdapterView.OnItemSelectedListener, View.OnClickListener {
    private static final String TAG = "DeviceFragment";

    private LeDeviceListAdapter mLeDeviceListAdapter;

    private boolean mScanning;
    private Handler mHandler;
    private String mDefaultDeviceAddress;
    private ParcelUuid mServiceUuid;
    private boolean mFirstTime = true;

    private FloatingActionButton mSelectButton;

    private BluetoothDevice mCurrentDevice;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private static final String ARG_DEFAULT_DEVICE_ADDRESS =
            "ARG_DEFAULT_DEVICE_ADDRESS";
    private static final String ARG_SERVICE_UUID =
            "ARG_SERVICE_UUID";

    // Fragments need an empty default contructor
    public DeviceFragment() { }

    public static Fragment newInstance(String defaultDeviceAddress, ParcelUuid serviceUuid) {
        Fragment fragment = new DeviceFragment();

        Bundle args = new Bundle();
        args.putString(ARG_DEFAULT_DEVICE_ADDRESS, defaultDeviceAddress);
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

    // Device scan callback.
    private ScanCallback mLeScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    mLeDeviceListAdapter.addDevice(result.getDevice());
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
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
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
        if (mCurrentDevice == null) return;
        if (mCurrentDevice.getAddress().equals(mDefaultDeviceAddress)) {
            mSelectButton.setVisibility(View.INVISIBLE);
        } else {
            mSelectButton.setVisibility(View.VISIBLE);
        }
        TextView status = (TextView) getView().findViewById(R.id.details_status);
        final int bondState = mCurrentDevice.getBondState();
        if (bondState == BluetoothDevice.BOND_BONDED) {
            status.setText("Connected");
        } else if (bondState == BluetoothDevice.BOND_BONDING) {
            status.setText("Connecting");
        } else {
            status.setText("Disconnected");
        }

        TextView name = (TextView) getView().findViewById(R.id.details_name);
        name.setText(mCurrentDevice.getName());

        TextView address = (TextView) getView().findViewById(R.id.details_address);
        address.setText(mCurrentDevice.getAddress());

        TextView rssi = (TextView) getView().findViewById(R.id.details_rssi);
        rssi.setText("???");

        TextView lastSeen = (TextView)  getView().findViewById(R.id.details_last_seen);
        lastSeen.setText("???");
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        mSelectButton.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onClick(View v) {
        if (mCurrentDevice == null) return;
        mDefaultDeviceAddress = mCurrentDevice.getAddress();
        mCallback.onDeviceSelected(mCurrentDevice);
        mLeDeviceListAdapter.notifyDataSetChanged();
        mSelectButton.setVisibility(View.INVISIBLE);
    }

    public void scanLeDevice(final boolean enable) {
        if (enable) {
            mLeDeviceListAdapter.clear();

            // Add the default device as the first item in the list
            mLeDeviceListAdapter.addDevice(mBluetoothAdapter.getRemoteDevice(mDefaultDeviceAddress));

            Log.d(TAG, "startBluetoothLeScan");
            // Stop any existing scan first
            mBluetoothLeScanner.stopScan(mLeScanCallback);
            // Start low power BT-LE scanning
            final ScanFilter scanFilter = new ScanFilter.Builder()
                    .setServiceUuid(mServiceUuid)
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

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = getActivity().getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
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

            BluetoothDevice device = mLeDevices.get(i);

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