package com.poyashimitter.accesssupporter;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.poyashimitter.accesssupporter.Setting.SettingActivity;

import org.opencv.android.OpenCVLoader;

import java.util.List;


public class MainActivity extends AppCompatActivity implements View.OnClickListener{
	Button notification;
	Button launchEkimemo;
	Button displayMap;
	
	TextView logTextView;
	BroadcastReceiver eventReceiver;
	IntentFilter eventFilter;
	
	
	
	static {
		System.loadLibrary("opencv_java3");
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_ACTION_BAR);
		
		setContentView(R.layout.activity_main);
		
		
		if(!OpenCVLoader.initDebug()){
			Log.i("OpenCV", "Failed");
		}else{
			Log.i("OpenCV", "successfully built !");
		}
		
		
		notification=(Button)findViewById(R.id.notification);
		if(serviceRunning()){//判定条件これで良い？？
			notification.setText("停止");
		}
		notification.setOnClickListener(this);
		
		launchEkimemo=(Button)findViewById(R.id.launchEkimemo);
		launchEkimemo.setOnClickListener(this);
		
		displayMap=(Button)findViewById(R.id.displayMap);
		displayMap.setOnClickListener(this);
		
		Intent in=new Intent(MainActivity.this,AccessSupporterService.class);
		startService(in.setAction("activityCreated"));
		
		logTextView=(TextView)findViewById(R.id.logTextView);
		
		
		//Serviceからのメッセージを受け取るための設定
		eventReceiver=new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if(logTextView!=null){
					String str=intent.getStringExtra(AccessSupporterService.STATUS_CHANGED);
					str+="\n"+logTextView.getText();
					str=str.substring(0,Math.min(str.length(),2000));
					logTextView.setText(str);
				}
			}
		};
		eventFilter=new IntentFilter();
		eventFilter.addAction(AccessSupporterService.STATUS_CHANGED);
		registerReceiver(eventReceiver,eventFilter);
	}
	
	
	
	@Override
	protected void onDestroy() {
		Intent in=new Intent(MainActivity.this,AccessSupporterService.class);
		startService(in.setAction("activityDestroyed"));
		unregisterReceiver(eventReceiver);
		super.onDestroy();
	}
	
	@Override
	public void onClick(View v) {
		switch(v.getId()){
			case R.id.notification:
				logTextView.setText("");
				Intent in=new Intent(MainActivity.this,AccessSupporterService.class);
				if(/*!serviceRunning()*/notification.getText().equals("開始")){
					startService(in.setAction("start"));
					notification.setText("停止");
				}else{
					startService(in.setAction("stop"));
					notification.setText("開始");
				}
				break;
			case R.id.launchEkimemo:
				try{
					PackageManager pm = getPackageManager();
					Intent intent = pm.getLaunchIntentForPackage("jp.mfapps.loc.ekimemo");
					startActivity(intent);
				}catch(Exception e){
					Toast.makeText(this,"駅メモがインストールされていません",Toast.LENGTH_SHORT).show();
				}
				break;
			case R.id.displayMap:
				break;
		}
		Log.d("AccessSupporter","onclick");
		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
			case R.id.setting:
				startActivity(new Intent(this, SettingActivity.class));
				break;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	boolean serviceRunning(){//AccessSupporterServiceが起動中か判定
		ActivityManager am = (ActivityManager)this.getSystemService(ACTIVITY_SERVICE);
		List<RunningServiceInfo> listServiceInfo = am.getRunningServices(Integer.MAX_VALUE);
		for (RunningServiceInfo curr : listServiceInfo) {
			//Log.d("AccessSupporter",curr.service.getClassName());
			// クラス名を比較
			if (curr.service.getClassName().equals(AccessSupporterService.class.getName())) {
				// 実行中のサービスと一致
				return true;
			}
		}
		return false;
	}
}
