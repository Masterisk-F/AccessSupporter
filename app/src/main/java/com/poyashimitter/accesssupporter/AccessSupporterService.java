package com.poyashimitter.accesssupporter;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.poyashimitter.accesssupporter.StationData.Station;
import com.poyashimitter.accesssupporter.StationData.StationHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Calendar;

import static android.location.GpsStatus.GPS_EVENT_SATELLITE_STATUS;

/**
 * Created by Tatu on 2016/10/07.
 */

public class AccessSupporterService extends Service implements LocationListener,GpsStatus.Listener,Runnable{
	NotificationManager notificationManager;
	
	LocationManager locationManager;
	
	StationHandler stationHandler;
	
	
	Bitmap largeIcon;//通知領域を表示した時に出てくるアイコン
	
	boolean isRunning=false;//最寄り駅通知を行っている間true
	
	Station currentStation;//現在の最寄り駅
	
	Location gpsLocation;//gpsによる最新の位置情報
	long gpsSignalTime=0;//gpsの最新の受信時刻
	Location networkLocation;//networkによる最新の位置情報
	long gpsEnabledTime=20000;//gpsを最後に受信してから、gpsが有効である時間[ms]
	final Object touchLock=new Object();//画面タッチのときにロックするオブジェクト
	
	Thread intervalsThread;//5分毎のタッチ処理で使うThreadをここに置いておく(割り込みを使うため)
	
	
	static String STATUS_CHANGED="AccessSupporter.StatusChanged";//Activityにメッセージを送るときのキー
	
	
	Thread th;//エラー画面の検出を繰り返し行う
	
	ImageProcesser imageProcesser;
	
	boolean screenOn=true;
	BroadcastReceiver screenActionReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action != null) {
				if (action.equals(Intent.ACTION_SCREEN_ON)) {
					// 画面ON時  
					Log.d("AccessSupporter", "SCREEN_ON");
					screenOn=true;
				} else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
					// 画面OFF時  
					Log.d("AccessSupporter", "SCREEN_OFF");
					screenOn=false;
				}
			}
		}
	};
	
	static {                                 // <-- 
		System.loadLibrary("opencv_java3");  // <-- この３行を追加
	}
	
	public AccessSupporterService(){
		super();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		locationManager=(LocationManager) getSystemService(Service.LOCATION_SERVICE);
		
		largeIcon= BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
		
		imageProcesser=new ImageProcesser(this);
		
		registerReceiver(screenActionReceiver,new IntentFilter(Intent.ACTION_SCREEN_ON));
		registerReceiver(screenActionReceiver,new IntentFilter(Intent.ACTION_SCREEN_OFF));
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(screenActionReceiver);
	}
	
	private void testPreferences(){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		
		Log.d("AccessSupporter","testPreferences!!");
		
		
		Log.d("AccessSupporter","contain_abolished_station:"+prefs.getBoolean("contain_abolished_station",false));
		Log.d("AccessSupporter","vibration:"+prefs.getString("vibration","when_needed"));
		Log.d("AccessSupporter","location:"+prefs.getString("location","both"));
		Log.d("AccessSupporter","min_update_time:"+prefs.getString("min_update_time","50"));
		
	}
	
	//startService(Intent)で呼び出される
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(intent==null){//念のため
			return super.onStartCommand(intent,flags,startId);
		}
		Log.d("AccessSupporter",intent.getAction());
		if(intent.getAction().equals("activityCreated")){
			if(stationHandler==null){
				try{
					stationHandler=new StationHandler(
							new InputStreamReader(getAssets().open("station20170403free - 2017-04-28.csv", AssetManager.ACCESS_BUFFER)),
							new InputStreamReader(getAssets().open("line20170403free.csv", AssetManager.ACCESS_BUFFER)),
							new InputStreamReader(getAssets().open("other_operating_station - 2017-04-28.csv", AssetManager.ACCESS_BUFFER)),
							new InputStreamReader(getAssets().open("abolished_station - 2017-03-20.csv", AssetManager.ACCESS_BUFFER)),
							new InputStreamReader(getAssets().open("shinkansen.csv")),
							new InputStreamReader(getAssets().open("abolished_line - 2017-03-19.csv")),
							PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
									.getBoolean("contain_abolished_station",true));
				}catch(IOException e){
					e.printStackTrace();
					Toast.makeText(this,"ファイルを読み込めません",Toast.LENGTH_SHORT).show();
				}
			}
			
			
		}else if(intent.getAction().equals("activityDestroyed")){
			if(!isRunning){
				stopSelf();//service終了
			}
		}else if(intent.getAction().equals("stop")){
			stopLocationUpdate();
			stopForeground(true);//foreground終了
			//stopSelf();//service終了
			isRunning=false;
			
			if(th!=null){
				th.interrupt();
			}
			th=null;
		}else if(intent.getAction().equals("start")){
			currentStation=null;
			gpsLocation=null;
			networkLocation=null;
			
			gpsSignalTime=0;
			
			isRunning=true;
			
			
			//最初に表示する通知
			NotificationCompat.Builder first = new NotificationCompat.Builder(getApplicationContext());
			first.setSmallIcon(R.mipmap.ic_launcher)
					.setLargeIcon(largeIcon)
					.setContentTitle("AccessSupporter")
					//.setContentText("AccessSupporter!!")
					.setVibrate(new long[]{0,100})
					.setContentIntent(
							PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT)
					);
			startForeground(1, first.build());//foregroundにする
			
			startLocationUpdate();
			
			screenOn=true;
			th=new Thread(this);
			th.start();
		}

		return super.onStartCommand(intent, flags, startId);
	}

	public void run(){
		
		while(th!=null){
			try{
				//エラー画面の検出
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
				if(!prefs.getBoolean("err_judge",false)){
					th=null;
					break;
				}
				touchToReset();
				Thread.sleep(Integer.valueOf(prefs.getString("err_judge_time","120"))*1000);
				//Thread.sleep(10000);
			}catch(InterruptedException e){
				//e.printStackTrace();
			}
		}
	}
	
	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	void startLocationUpdate(){//位置情報開始
		if(locationManager==null){
			Log.d("AccessSupporter","locationManager is null");
			return;
		}
		
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		int minUpdateDistance=Integer.valueOf(prefs.getString("min_update_distance","50"));
		int minUpdateTime=Integer.valueOf(prefs.getString("min_update_time","5"));
		
		
		if (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
				==PackageManager.PERMISSION_GRANTED){
			
			locationManager.requestLocationUpdates(
					LocationManager.GPS_PROVIDER,
					minUpdateTime,
					minUpdateDistance,
					this
			);
			
			
		}
		if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)
				==PackageManager.PERMISSION_GRANTED){
			locationManager.requestLocationUpdates(
					LocationManager.NETWORK_PROVIDER,
					minUpdateTime,
					minUpdateDistance,
					this
			);
		}
		
		locationManager.addGpsStatusListener(this);
		
		//Log.d("AccessSupporter","startLocationUpdate() finished");
	}

	void stopLocationUpdate(){//位置情報終了
		if (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
				==PackageManager.PERMISSION_GRANTED
				||ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)
				==PackageManager.PERMISSION_GRANTED){
			locationManager.removeUpdates(this);
		}
		
	}

	
	//位置情報が更新されたとき呼ばれる
	@Override
	public void onLocationChanged(Location location) {
		Log.d("AccessSupporter","onLocationChanged : provider="+location.getProvider()
				+", location="+location.getLongitude()+", "+location.getLatitude());
		
		sendToActivity("onLocationChanged : \n	provider="+location.getProvider()
				+", location="+location.getLongitude()+", "+location.getLatitude());
		
		if(location.getProvider().equals("gps")){
			gpsLocation=location;
			gpsSignalTime= Calendar.getInstance().getTimeInMillis();
		}else if(location.getProvider().equals("network")){
			
			if(networkLocation!=null && networkLocation.distanceTo(location)<1){//位置が変化しているかどうか
				return;
			}
			networkLocation=location;
			if(Calendar.getInstance().getTimeInMillis() < gpsSignalTime+gpsEnabledTime){
				return;
			}
		}else{
			return;
		}
		
		long start=System.currentTimeMillis();
		Station st=stationHandler.getNearestStation(location.getLongitude(),location.getLatitude());
		Log.d("AccessSupporter",
				st.getStationName()+" "+"E"+st.getLongitude()+",N"+st.getLatitude()
				+"\ntime : "+(System.currentTimeMillis()-start)+"ms");
		if(currentStation!=null && currentStation.equals(st)){
			return;
		}
		
		start=System.currentTimeMillis();
		Station tmp=stationHandler.getField(location.getLongitude(),location.getLatitude());
		Log.d("AccessSupporter","getField : "+
				tmp.getStationName()
						+"\ntime : "+(System.currentTimeMillis()-start)+"ms");
		
		
		//最寄り駅が変化したとき、ここより下の処理に進む
		
		NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
		builder.setSmallIcon(R.mipmap.ic_launcher)
				.setLargeIcon(largeIcon)
				.setContentTitle("AccessSupporter")
				.setContentText("最寄り駅 : "+st.getStationName()+" ("+location.getProvider()+")")
				.setContentIntent(
						PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT)
				);//.setVibrate(new long[]{0,200,100,200,100,100});
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String vibration=prefs.getString("vibration","when_needed");
		if(vibration.equals("true") || (vibration.equals("when_needed") && !ekimemoIsForeground())){
			builder.setVibrate(new long[]{0,400,100,400,});
		}
		
		notificationManager.notify(1, builder.build());
		
		currentStation=st;
		
		
		//画面タッチ処理
		touchToAccess();
		
		//5分毎タッチ
		setIntervalsTouch();
		
	}
	
	
	private void touchToReset() throws InterruptedException{
		if(!screenOn || !ekimemoIsForeground() || !imageProcesser.ekimemoIsError()){
			return;
		}
		Log.d("AccessSupporter","touchToReset() : ekimemoIsError()=true");
		synchronized(touchLock){
			try{
				Runtime.getRuntime().exec("adb shell input touchscreen tap 350 850");
				Thread.sleep(17000);
				
			}catch(IOException e){
				e.printStackTrace();
			}
		}
		touchToReset();
	}
	
	private void touchToAccess(){
		if(!ekimemoIsForeground())
			return;
		
		new Thread(new Runnable(){
			@Override
			public void run() {
				synchronized(touchLock){
					try{
						//Runtime.getRuntime().exec("adb connect 127.0.0.1").waitFor();
						//Log.d("AccessSupporter","adb connect finished");
						
						SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
						if(prefs.getBoolean("change_denco",false)){
							Runtime.getRuntime().exec("adb shell input touchscreen tap 700 880");
							Thread.sleep(1000);
						}
						
						Runtime.getRuntime().exec("adb shell input touchscreen tap 650 1200");
						Thread.sleep(5500);
						Runtime.getRuntime().exec("adb shell input touchscreen tap 650 1200");
						Thread.sleep(1000);
						
						
					}catch(Exception e){}
				}
			}
		}).start();
	}
	
	private void setIntervalsTouch(){//(設定されていれば)5分毎にタッチ
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		if(!prefs.getBoolean("five_min_access",false))
			return;
		
		if(intervalsThread!=null){
			intervalsThread.interrupt();
		}
		
		final boolean random=prefs.getBoolean("intervals_random",false);
		
		intervalsThread=new Thread(new Runnable() {
			@Override
			public void run() {
				try{
					if(random){
						Thread.sleep(305000+(long)(Math.random()*55000));
					}else{
						Thread.sleep(305000);
					}
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
					if(prefs.getBoolean("five_min_access",false)){
						touchToAccess();
						setIntervalsTouch();
					}
					long start=System.currentTimeMillis();
					ekimemoIsForeground();
					Log.d("AccessSupporter","ekimemoIsForeground() time : "+(System.currentTimeMillis()-start)+"ms");
				}catch(InterruptedException e){
					//e.printStackTrace();
				}
			}
		});
		intervalsThread.start();
	}
	
	
	@Override
	public void onProviderDisabled(String provider) {
		Log.d("AccessSupporter","onProviderDisabled:"+provider);
		sendToActivity("onProviderDisabled:"+provider);
	}

	@Override
	public void onProviderEnabled(String provider) {
		//Log.d("AccessSupporter","onProviderEnabled:"+provider);
		sendToActivity("onProviderEnabled:"+provider);
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		//Log.d("AccessSupporter","onStatusChanged");
		sendToActivity("onStatusChanged");
	}
	
	
	
	//GpsStatus.listenerのメソッド
	@Override
	public void onGpsStatusChanged(int event){
		switch(event){
			case GpsStatus.GPS_EVENT_FIRST_FIX:
				//Log.d("AccessSupporter","onGpsStatusChanged : GPS_EVENT_FIRST_FIX");
				sendToActivity("onGpsStatusChanged : \n	GPS_EVENT_FIRST_FIX");
				break;
			case GPS_EVENT_SATELLITE_STATUS:
				//Log.d("AccessSupporter","onGpsStatusChanged : GPS_EVENT_SATELLITE_STATUS");
				//sendToActivity("onGpsStatusChanged : \n	GPS_EVENT_SATELLITE_STATUS");
				
				break;
			case GpsStatus.GPS_EVENT_STARTED:
				//Log.d("AccessSupporter","onGpsStatusChanged : GPS_EVENT_STARTED");
				sendToActivity("onGpsStatusChanged : \n	GPS_EVENT_STARTED");
				break;
			case GpsStatus.GPS_EVENT_STOPPED:
				//Log.d("AccessSupporter","onGpsStatusChanged : GPS_EVENT_STOPPED");
				sendToActivity("onGpsStatusChanged : \n	GPS_EVENT_STOPPED");
				break;
		}
	}
	
	
	boolean ekimemoIsForeground(){
		
		UsageEvents.Event event=getForegroundApp();
		
		return event!=null && event.getPackageName().equals("jp.mfapps.loc.ekimemo");
	}
	
	UsageEvents.Event getForegroundApp(){
		long start=System.currentTimeMillis()-1000*60*60*24;
		long end=System.currentTimeMillis()+100;
		
		UsageStatsManager stats = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE/*"usagestats"*/);
		UsageEvents usageEvents = stats.queryEvents(start, end);//usegeEventsのうち後ろにあるほど新しいevent
		UsageEvents.Event event=null;
		//Log.d("AccessSupporter","-----stats.queryEvents-----");
		while (usageEvents.hasNextEvent()) {
			event = new android.app.usage.UsageEvents.Event();
			usageEvents.getNextEvent(event);
			/*
			long timestamp = event.getTimeStamp();
			String packageName = event.getPackageName();
			String className = event.getClassName();
			int type = event.getEventType();
			String stype="";
			if(type==UsageEvents.Event.MOVE_TO_BACKGROUND)
				stype="MOVE_TO_BACKGROUND";
			else if(type==UsageEvents.Event.MOVE_TO_FOREGROUND)
				stype="MOVE_TO_FOREGROUND";
			
			//Log.d("AccessSupporter","stats::"+timestamp+", "+packageName+", "+className+", "+stype);
			*/
		}
		//Log.d("AccessSupporter","-----stats.queryEvents end-----");
		
		return event;
		
		
		/*
		//テスト (参考 : http://www.atmarkit.co.jp/ait/articles/1602/01/news156_4.html)
		List<UsageStats> list = stats.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end);
		String packageName=null;
		if (list != null && list.size() > 0) {
			SortedMap<Long, UsageStats> map = new TreeMap<>();
			for (UsageStats usageStats : list) {
				map.put(usageStats.getLastTimeUsed(), usageStats);
			}
			if (!map.isEmpty()) {
				packageName = map.get(map.lastKey()).getPackageName();
			}
		}
		Log.d("AccessSupporter","stats.queryUsageStatsによるforeground app : "+packageName);
		*/
		
	}
	
	private void sendToActivity(String message){
		sendBroadcast(new Intent(STATUS_CHANGED).putExtra(STATUS_CHANGED,message));
	}
}


