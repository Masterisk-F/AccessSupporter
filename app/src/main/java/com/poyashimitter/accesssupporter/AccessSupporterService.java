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
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.cgutman.adblib.AdbBase64;
import com.cgutman.adblib.AdbConnection;
import com.cgutman.adblib.AdbCrypto;
import com.cgutman.adblib.AdbStream;
import com.poyashimitter.accesssupporter.StationData.Station;
import com.poyashimitter.accesssupporter.StationData.StationHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;

import static android.location.GpsStatus.GPS_EVENT_SATELLITE_STATUS;

public class AccessSupporterService extends Service implements LocationListener,GpsStatus.Listener,Runnable{
	NotificationManager notificationManager;
	
	LocationManager locationManager;
	
	StationHandler stationHandler;
	
	
	SharedPreferences prefs;
	
	Bitmap largeIcon;//通知領域を表示した時に出てくるアイコン
	
	private boolean isRunning=false;//最寄り駅通知を行っている間true
	
	Station currentStation;//現在の最寄り駅
	
	Location currentLocation;//最新の位置情報
	long gpsSignalTime=0;//gpsの最新の受信時刻
	long gpsEnabledTime=20000;//gpsを最後に受信してから、gpsが有効である時間[ms]
	final Object touchLock=new Object();//画面タッチのときにロックするオブジェクト
	
	Thread intervalsThread;//5分毎のタッチ処理で使うThreadをここに置いておく(割り込みを使うため)
	
	
	//broadcastのキー
	//log用
	public static String STATUS_CHANGED="com.poyashimitter.AccessSupporter.StatusChanged";
	//位置情報更新時
	public static String LOCATION_CANGED="com.poyashimitter.AccessSupporter.LocationChanged";
	//最寄り駅変更時
	public static String NEAREST_STATION_CHANGED="com.poyashimitter.AccessSupporter.NearestStationChanged";
	
	Thread th;//エラー画面の検出を繰り返し行う
	
	ImageProcesser imageProcesser;
	
	
	AdbStream adbStream;//adb接続で使う
	
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
		
		prefs=PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		
		((AccessSupporterApplication)getApplication()).setAccessSupporterService(this);
		
		connectAdb();
		
		imageProcesser=new ImageProcesser(this);
		
		registerReceiver(screenActionReceiver,new IntentFilter(Intent.ACTION_SCREEN_ON));
		registerReceiver(screenActionReceiver,new IntentFilter(Intent.ACTION_SCREEN_OFF));
		
		//((AccessSupporterApplication)getApplication()).setAccessSupporterService(this);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(screenActionReceiver);
		
		//念のため
		//((AccessSupporterApplication)getApplication()).setNotificationIsRunning(false);
		
		((AccessSupporterApplication)getApplication()).setAccessSupporterService(null);
		
		
		
		if(adbStream!=null){
			Thread th=new Thread(new Runnable() {
				@Override
				public void run() {
					try{//UIスレッドでは弄れないため別スレッドで
						adbStream.write(" exit\n");
						
					}catch(IOException e){
						e.printStackTrace();
					}catch(InterruptedException e){
						e.printStackTrace();
					}
				}
			});
			th.start();
			try{
				th.join();
			}catch(InterruptedException e){
				e.printStackTrace();
			}
		}
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
							new InputStreamReader(getAssets().open("station20170403free - 2017-09-09.csv", AssetManager.ACCESS_BUFFER)),
							new InputStreamReader(getAssets().open("line20170403free.csv", AssetManager.ACCESS_BUFFER)),
							new InputStreamReader(getAssets().open("other_operating_station - 2017-09-09.csv", AssetManager.ACCESS_BUFFER)),
							new InputStreamReader(getAssets().open("abolished_station - 2017-08-13.csv", AssetManager.ACCESS_BUFFER)),
							new InputStreamReader(getAssets().open("shinkansen.csv")),
							new InputStreamReader(getAssets().open("abolished_line - 2017-08-13.csv")),
							prefs
									.getBoolean("contain_abolished_station",true));
					
					//Applicationに登録
					((AccessSupporterApplication)getApplication()).setStationHandler(stationHandler);
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
			currentLocation=null;
			
			gpsSignalTime=0;
			
			isRunning=true;
			
			//最初に表示する通知
			NotificationCompat.Builder first = new NotificationCompat.Builder(getApplicationContext());
			first.setSmallIcon(R.mipmap.eki_1)
					.setLargeIcon(largeIcon)
					.setContentTitle("AccessSupporter")
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

	public boolean isRunning(){
		return isRunning;
	}
	
	public void run(){
		
		while(th!=null){
			try{
				//エラー画面の検出
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
			
			Log.d("AccessSupporter","requestLocationUpdates(),gps");
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
		
		onStatusChangedBroadcast("onLocationChanged : \n	provider="+location.getProvider()
				+", location="+location.getLongitude()+", "+location.getLatitude());
		
		if(location.getProvider().equals("gps")){
			currentLocation=location;
			gpsSignalTime= Calendar.getInstance().getTimeInMillis();
		}else if(location.getProvider().equals("network")){
			/*
			if(networkLocation!=null && networkLocation.distanceTo(location)<1){//位置が変化しているかどうか
				return;
			}
			networkLocation=location;*/
			if(Calendar.getInstance().getTimeInMillis() < gpsSignalTime+gpsEnabledTime){
				return;
			}
			currentLocation=location;
		}else{
			return;
		}
		
		//((AccessSupporterApplication)getApplication()).setCurrentLocation(location);
		onLocationChangedBroadcast(location);
		
		long start=System.currentTimeMillis();
		Station st=stationHandler.getNearestStation(location.getLongitude(),location.getLatitude());
		Log.d("AccessSupporter",
				st.getStationName()+" "+"E"+st.getLongitude()+",N"+st.getLatitude()
				+"\ntime : "+(System.currentTimeMillis()-start)+"ms");
		if(currentStation!=null && currentStation.equals(st)){
			return;
		}
		/*
		//test
		start=System.currentTimeMillis();
		Station tmp=stationHandler.getField(location.getLongitude(),location.getLatitude());
		Log.d("AccessSupporter","getField : "+
				tmp.getStationName()
						+"\ntime : "+(System.currentTimeMillis()-start)+"ms");
		*/
		
		//最寄り駅が変化したとき、ここより下の処理に進む
		onNearestStationChangedBroadcast(st);
		
		NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
		builder.setSmallIcon(R.mipmap.eki_1)
				.setLargeIcon(largeIcon)
				.setContentTitle("AccessSupporter")
				.setContentText("最寄り駅 : "+st.getStationName()+" ("+location.getProvider()+")")
				.setContentIntent(
						PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT)
				);//.setVibrate(new long[]{0,200,100,200,100,100});
		
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
		if(!screenOn || adbStream==null || !ekimemoIsForeground()){
			return;
		}
		
		int n;
		try{
			n=imageProcesser.ekimemoIsError();
		}catch(Exception e){//たまにエラーが起きるため念のため
			Log.d("AccessSupporter","touchToReset() : Unknown Error");
			e.printStackTrace();
			return;
		}
		switch(n){
			case ImageProcesser.ERROR:
				Log.d("AccessSupporter","touchToReset() : ekimemoIsError()=ImageProcesser.ERROR");
				synchronized(touchLock){
					try{
						//Runtime.getRuntime().exec("adb shell input touchscreen tap 350 850");
						adbStream.write(" input touchscreen tap 550 1170\n");
						Thread.sleep(17000);
					}catch(IOException e){
						e.printStackTrace();
					}
				}
				touchToReset();
				
				break;
			case ImageProcesser.CONNECTION_ERROR:
				Log.d("AccessSupporter","touchToReset() : ekimemoIsError()=ImageProcesser.CONNECTION_ERROR");
				synchronized(touchLock){
					try{
						adbStream.write(" input touchscreen tap 450 1090\n");
						Thread.sleep(17000);
					}catch(IOException e){
						e.printStackTrace();
					}
				}
				touchToReset();
				
				break;
			case ImageProcesser.NORMAL:
				break;
		}
		
		
	}
	
	private void touchToAccess(){
		if(!ekimemoIsForeground() || adbStream==null)
			return;
		
		new Thread(new Runnable(){
			@Override
			public void run() {
				synchronized(touchLock){
					try{
						if(prefs.getBoolean("change_denco",false)){
							adbStream.write(" input touchscreen tap 1040 1250\n");
							Thread.sleep(1000);
						}
						
						adbStream.write(" input touchscreen tap 1000 1700\n");
						Thread.sleep(5500);
						adbStream.write(" input touchscreen tap 1000 1700\n");
						Thread.sleep(1000);
						
						
					}catch(Exception e){}
				}
			}
		}).start();
	}
	
	private void setIntervalsTouch(){//(設定されていれば)5分毎にタッチ
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
					if(prefs.getBoolean("five_min_access",false)){
						touchToAccess();
						setIntervalsTouch();
					}
					/*
					long start=System.currentTimeMillis();
					ekimemoIsForeground();
					Log.d("AccessSupporter","ekimemoIsForeground() time : "+(System.currentTimeMillis()-start)+"ms");
					*/
				}catch(InterruptedException e){
					//e.printStackTrace();
				}
			}
		});
		intervalsThread.start();
	}
	
	
	@Override
	public void onProviderDisabled(String provider) {
		//Log.d("AccessSupporter","onProviderDisabled:"+provider);
		onStatusChangedBroadcast("onProviderDisabled:"+provider);
	}

	@Override
	public void onProviderEnabled(String provider) {
		//Log.d("AccessSupporter","onProviderEnabled:"+provider);
		onStatusChangedBroadcast("onProviderEnabled:"+provider);
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		//Log.d("AccessSupporter","onStatusChanged");
		onStatusChangedBroadcast("onStatusChanged");
	}
	
	
	
	//GpsStatus.listenerのメソッド
	@Override
	public void onGpsStatusChanged(int event){
		switch(event){
			case GpsStatus.GPS_EVENT_FIRST_FIX:
				//Log.d("AccessSupporter","onGpsStatusChanged : GPS_EVENT_FIRST_FIX");
				onStatusChangedBroadcast("onGpsStatusChanged : \n	GPS_EVENT_FIRST_FIX");
				break;
			case GPS_EVENT_SATELLITE_STATUS:
				//Log.d("AccessSupporter","onGpsStatusChanged : GPS_EVENT_SATELLITE_STATUS");
				//sendToActivity("onGpsStatusChanged : \n	GPS_EVENT_SATELLITE_STATUS");
				
				break;
			case GpsStatus.GPS_EVENT_STARTED:
				//Log.d("AccessSupporter","onGpsStatusChanged : GPS_EVENT_STARTED");
				onStatusChangedBroadcast("onGpsStatusChanged : \n	GPS_EVENT_STARTED");
				break;
			case GpsStatus.GPS_EVENT_STOPPED:
				//Log.d("AccessSupporter","onGpsStatusChanged : GPS_EVENT_STOPPED");
				onStatusChangedBroadcast("onGpsStatusChanged : \n	GPS_EVENT_STOPPED");
				break;
		}
	}
	
	/*
	boolean ekimemoIsForeground(){
		
		UsageEvents.Event event=getForegroundApp();
		
		return event!=null && event.getPackageName().equals("jp.mfapps.loc.ekimemo");
	}
	*/
	boolean ekimemoIsForeground(){
		long start=System.currentTimeMillis()-1000*60*60*24;
		long end=System.currentTimeMillis()+100;
		
		UsageStatsManager stats = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE/*"usagestats"*/);
		UsageEvents usageEvents = stats.queryEvents(start, end);//usegeEventsのうち後ろにあるほど新しいevent
		UsageEvents.Event event=null;
		
		
		boolean isForeground=false;
		while (usageEvents.hasNextEvent()) {
			event = new android.app.usage.UsageEvents.Event();
			usageEvents.getNextEvent(event);
			/*
			//log用
			String type;
			switch(event.getEventType()){
				case UsageEvents.Event.MOVE_TO_BACKGROUND:
					type="MOVE_TO_BACKGROUND";
					break;
				case UsageEvents.Event.MOVE_TO_FOREGROUND:
					type="MOVE_TO_FOREGROUND";
					break;
				case UsageEvents.Event.CONFIGURATION_CHANGE:
					type="CONFIGURATION_CHANGE";
					break;
				case UsageEvents.Event.USER_INTERACTION:
					type="USER_INTERACTION";
					break;
				default:
					type="Unknown";
					break;
			}
			Log.d("AccessSupporter","app usage:"+event.getTimeStamp()+":"+event.getPackageName()+","+type);
			*/
			if(event.getPackageName().equals("jp.mfapps.loc.ekimemo")){
				switch(event.getEventType()){
					case UsageEvents.Event.MOVE_TO_BACKGROUND:
						isForeground=false;
						break;
					case UsageEvents.Event.MOVE_TO_FOREGROUND:
						isForeground=true;
						break;
					default:
						break;
				}
			}
		}
		
		Log.d("AccessSupporter","ekimemoIsForeground()="+isForeground);
		return isForeground;
	}
	
	private void onStatusChangedBroadcast(String message){
		((AccessSupporterApplication)getApplication()).addLogString(message);
		sendBroadcast(new Intent(STATUS_CHANGED).putExtra(STATUS_CHANGED,message));
	}
	
	private void onLocationChangedBroadcast(Location loc){
		((AccessSupporterApplication)getApplication()).setCurrentLocation(loc);
		sendBroadcast(new Intent(LOCATION_CANGED));
	}
	private void onNearestStationChangedBroadcast(Station sta){
		((AccessSupporterApplication)getApplication()).addStationHistory(sta);
		sendBroadcast(new Intent(NEAREST_STATION_CHANGED));
	}
	
	
	
	private void connectAdb(){
		final Handler handler=new Handler();
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				Socket socket;
				AdbCrypto crypto=null;
				
				AdbConnection adb=null;
				
				AdbBase64 base64Impl=new AdbBase64() {
					@Override
					public String encodeToString(byte[] data) {
						return android.util.Base64.encodeToString(data, 16);
					}
				};
				
				try {
					crypto = AdbCrypto.generateAdbKeyPair(base64Impl);
					
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
					return;
				}
				
				// Connect the socket to the remote host
				Log.d("AdbTest","Socket connecting...");
				try {
					socket = new Socket("localhost", 5555);
				} catch (UnknownHostException e) {
					e.printStackTrace();
					return;
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
				Log.d("AdbTest","Socket connected");
				
				// Construct the AdbConnection object
				try {
					adb = AdbConnection.create(socket, crypto);
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
				
				
				// Start the application layer connection process
				Log.d("AdbTest","ADB connecting...");
				try {
					adb.connect();
				} catch (IOException e) {
					e.printStackTrace();
					return;
				} catch (InterruptedException e) {
					e.printStackTrace();
					return;
				}
				Log.d("AdbTest","ADB connected");
				
				// Open the shell stream of ADB
				try {
					adbStream = adb.open("shell:");
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
					return;
				} catch (IOException e) {
					e.printStackTrace();
					return;
				} catch (InterruptedException e) {
					e.printStackTrace();
					return;
				}
				
				// Start the receiving thread
				new Thread(new Runnable() {
					@Override
					public void run() {
						Log.d("AdbTest","receiving thread start");
						while (!adbStream.isClosed()) {
							try {
								// Print each thing we read from the shell stream
								Log.d("AdbTest",new String(adbStream.read(), "US-ASCII"));
							} catch (UnsupportedEncodingException e) {
								e.printStackTrace();
								return;
							} catch (InterruptedException e) {
								e.printStackTrace();
								return;
							} catch (IOException e) {
								e.printStackTrace();
								return;
							}
						}
					}
				}).start();
				
				
				//UIスレッド以外からtoastを表示
				handler.post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getApplicationContext(),"adb接続に成功しました",Toast.LENGTH_LONG).show();
					}
				});
				
				while(true){
					if(imageProcesser!=null){
						imageProcesser.setAdbStream(adbStream);
						break;
					}
					try{
						Thread.sleep(100);
					}catch(InterruptedException e){}
				}
			}
		}
		).start();
	}
	
	
	
}


