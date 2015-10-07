package net.jpuderer.android.bluedoor;

import android.app.Fragment;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public  class LockPreferenceFragment extends PreferenceFragment {

    public static Fragment newInstance() {
        Fragment fragment = new LockPreferenceFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        //bindPreferenceSummaryToValue(findPreference("example_text"));
    }
}