package net.jpuderer.android.bluedoor;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class DoorControlFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "DoorControlFragment";

    private int mConnectionState = DoorlockService.STATE_DISCONNECTED;
    private int mDoorState = DoorlockService.DOOR_STATE_UNKNOWN;

    private Button mLockButton;
    private TextView mTextView;

    private static final String ARG_CONNECTION_STATE = "ARG_DEFAULT_DEVICE_ADDRESS";
    private static final String ARG_DOOR_STATE = "ARG_DEFAULT_DEVICE_ADDRESS";

    // Fragments need an empty default contructor
    public DoorControlFragment() { }

    public static Fragment newInstance() {
        Fragment fragment = new DoorControlFragment();
        return fragment;
    }

    DoorControlFragmentListener mCallback;

    // Container Activity must implement this interface
    public interface DoorControlFragmentListener {
        public void onLockDoor(boolean lock);
        public void onUpdateView();
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.door_control_fragment, container, false);
        mLockButton = (Button) rootView.findViewById(R.id.lock_button);
        mTextView = (TextView) rootView.findViewById(R.id.lock_button_text);
        mLockButton.setOnClickListener(this);

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mCallback = (DoorControlFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement DoorControlFragmentListener");
        }
    }

    @Override
    public void onResume() {
        mCallback.onUpdateView();
        super.onResume();
    }

    @Override
    public void onClick(View v) {
        if (mDoorState == DoorlockService.DOOR_STATE_LOCKED) {
            mCallback.onLockDoor(false);
        } else if (mDoorState == DoorlockService.DOOR_STATE_UNLOCKED) {
            mCallback.onLockDoor(true);
        }
    }

    public void updateState(int connectionState, int doorState) {
        mConnectionState = connectionState;
        mDoorState = doorState;
        if (mConnectionState != DoorlockService.STATE_CONNECTED) {
            mLockButton.setEnabled(false);
            mTextView.setText("");
        } else if (mDoorState == DoorlockService.DOOR_STATE_LOCKED) {
            mLockButton.setEnabled(true);
            mLockButton.setSelected(true);
            mTextView.setText("locked");
        } else if (mDoorState == DoorlockService.DOOR_STATE_UNLOCKED) {
            mLockButton.setEnabled(true);
            mLockButton.setSelected(false);
            mTextView.setText("unlocked");
        } else {
            // Connected, but door is in an unknown state
            mLockButton.setEnabled(false);
            mTextView.setText("--");
        }
    }
}