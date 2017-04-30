package com.poyashimitter.accesssupporter.Setting;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.poyashimitter.accesssupporter.R;

public class SettingPreferenceFragment extends PreferenceFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.preference);
	}
}
