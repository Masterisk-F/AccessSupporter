package com.poyashimitter.accesssupporter;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import com.poyashimitter.accesssupporter.Setting.SettingActivity;
import com.poyashimitter.accesssupporter.Tabs.HistoryOfStationsFragment;
import com.poyashimitter.accesssupporter.Tabs.LogFragment;
import com.poyashimitter.accesssupporter.Tabs.NearbyStationsFragment;

import org.opencv.android.OpenCVLoader;

import java.util.List;


public class MainActivity extends AppCompatActivity 
		implements View.OnClickListener,NearbyStationsFragment.OnFragmentInteractionListener,
		HistoryOfStationsFragment.OnFragmentInteractionListener,
		LogFragment.OnFragmentInteractionListener{
	Button notification;
	Button launchEkimemo;
	Button displayMap;
	
	/*TextView logTextView;
	BroadcastReceiver eventReceiver;
	IntentFilter eventFilter;
	*/
	
	
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
		
		setTabAndPager();
		
		
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
		/*
		logTextView=(TextView)findViewById(logTextView);
		
		
		//Serviceからのメッセージを受け取るための設定
		eventReceiver=new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if(logTextView!=null){
					String str=intent.getStringExtra(AccessSupporterService.STATUS_CHANGED);
					str+="\n"+logTextView.getText();
					str=str.substring(0,Math.min(str.length(),4000));
					logTextView.setText(str);
				}
			}
		};
		eventFilter=new IntentFilter();
		eventFilter.addAction(AccessSupporterService.STATUS_CHANGED);
		registerReceiver(eventReceiver,eventFilter);*/
	}
	
	
	void setTabAndPager(){
		final String[] title={"nearby stations","history","log"};
		
		TabLayout tabLayout=(TabLayout)findViewById(R.id.tabs);
		for(int i=0;i<title.length;i++){
			tabLayout.addTab(tabLayout.newTab());
		}
		
		ViewPager viewPager=(ViewPager)findViewById(R.id.pager);
		viewPager.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager()) {
			@Override
			public Fragment getItem(int position) {
				Fragment fragment=null;
				switch(position){
					case 0:
						fragment= HistoryOfStationsFragment.newInstance(null,null);
						break;
					case 1:
						fragment= NearbyStationsFragment.newInstance(null,null);
						break;
					case 2:
						fragment= LogFragment.newInstance(null,null);
						break;
				}
				return fragment;
			}
			
			@Override
			public int getCount() {
				return title.length;
			}
			
			@Override
			public CharSequence getPageTitle(int position) {
				return title[position];
			}
		});
		
		viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
				
			}
			
			@Override
			public void onPageSelected(int position) {
				
			}
			
			@Override
			public void onPageScrollStateChanged(int state) {
				
			}
		});
		tabLayout.setupWithViewPager(viewPager);
	}
	
	
	@Override
	public void onFragmentInteraction(Uri uri) {
		
	}
	
	@Override
	protected void onDestroy() {
		Intent in=new Intent(MainActivity.this,AccessSupporterService.class);
		startService(in.setAction("activityDestroyed"));
		//unregisterReceiver(eventReceiver);
		super.onDestroy();
	}
	
	@Override
	public void onClick(View v) {
		switch(v.getId()){
			case R.id.notification:
				//logTextView.setText("");
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
			// クラス名を比較
			if (curr.service.getClassName().equals(AccessSupporterService.class.getName())) {
				// 実行中のサービスと一致
				return true;
			}
		}
		return false;
	}
}
