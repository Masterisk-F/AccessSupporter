package com.poyashimitter.accesssupporter.Setting;

import android.app.Activity;
import android.os.Bundle;

/**
 * Created by Tatu on 2016/10/21.
 */

public class SettingActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		
		getFragmentManager()
				.beginTransaction()
				.replace(android.R.id.content,new SettingPreferenceFragment())
				.commit();
	}
}
