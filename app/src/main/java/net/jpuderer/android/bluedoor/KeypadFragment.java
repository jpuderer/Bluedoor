package net.jpuderer.android.bluedoor;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class KeypadFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "KeypadFragment";

    private int mConnectionState = DoorlockService.STATE_DISCONNECTED;
    private int mDoorState = DoorlockService.DOOR_STATE_UNKNOWN;

    private static final String ARG_CONNECTION_STATE = "ARG_DEFAULT_DEVICE_ADDRESS";
    private static final String ARG_DOOR_STATE = "ARG_DEFAULT_DEVICE_ADDRESS";

    // Fragments need an empty default contructor
    public KeypadFragment() { }

    public static Fragment newInstance() {
        Fragment fragment = new KeypadFragment();
        return fragment;
    }

    KeypadFragmentListener mCallback;

    // Container Activity must implement this interface
    public interface KeypadFragmentListener {
        public void onSendCommand(byte b);
        public void onUpdateView();
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.keypad_fragment, container, false);
        rootView.findViewById(R.id.key_0).setOnClickListener(this);
        rootView.findViewById(R.id.key_1).setOnClickListener(this);
        rootView.findViewById(R.id.key_2).setOnClickListener(this);
        rootView.findViewById(R.id.key_3).setOnClickListener(this);
        rootView.findViewById(R.id.key_4).setOnClickListener(this);
        rootView.findViewById(R.id.key_5).setOnClickListener(this);
        rootView.findViewById(R.id.key_6).setOnClickListener(this);
        rootView.findViewById(R.id.key_7).setOnClickListener(this);
        rootView.findViewById(R.id.key_8).setOnClickListener(this);
        rootView.findViewById(R.id.key_9).setOnClickListener(this);
        rootView.findViewById(R.id.key_enter).setOnClickListener(this);
        rootView.findViewById(R.id.key_cancel).setOnClickListener(this);

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mCallback = (KeypadFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement KeypadFragmentListener");
        }
    }

    @Override
    public void onResume() {
        mCallback.onUpdateView();
        super.onResume();
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.key_0:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_0);
                break;
            case R.id.key_1:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_1);
                break;
            case R.id.key_2:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_2);
                break;
            case R.id.key_3:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_3);
                break;
            case R.id.key_4:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_4);
                break;
            case R.id.key_5:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_5);
                break;
            case R.id.key_6:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_6);
                break;
            case R.id.key_7:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_7);
                break;
            case R.id.key_8:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_8);
                break;
            case R.id.key_9:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_9);
                break;
            case R.id.key_enter:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_ENTER);
                break;
            case R.id.key_cancel:
                mCallback.onSendCommand(DoorlockService.KEYPAD_COMMAND_KEY_CANCEL);
                break;
            default:
                Log.w(TAG, "Unexpected click event");
                break;
        }
    }

    public void updateState(int connectionState, int doorState) {
        mConnectionState = connectionState;
        mDoorState = doorState;
        TextView connectionStatus = (TextView) getView().findViewById(R.id.text_connection_status);
        if (mConnectionState == DoorlockService.STATE_CONNECTED) {
            connectionStatus.setText("Connected");
        } else {
            connectionStatus.setText("Disconnected");
        }

        TextView doorStatus = (TextView) getView().findViewById(R.id.text_door_status);
        if (mDoorState == DoorlockService.DOOR_STATE_UNLOCKED) {
            doorStatus.setText("Unlocked");
        } else if (mDoorState == DoorlockService.DOOR_STATE_LOCKED) {
            doorStatus.setText("Locked");
        } else {
            doorStatus.setText("--");
        }
    }
}