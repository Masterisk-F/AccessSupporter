package com.poyashimitter.accesssupporter;

import android.app.Application;

import com.poyashimitter.accesssupporter.StationData.StationHandler;

/**
 * Created by FT on 2017/08/02.
 */

public class AccessSupporterApplication extends Application {
	
	private StationHandler stationHandler;
	private AccessSupporterService service;
	
	
	public void setStationHandler(StationHandler handler){
		stationHandler=handler;
	}
	public StationHandler getStationHandler(){
		return stationHandler;
	}
	
	public void setAccessSupporterService(AccessSupporterService acs){
		service=acs;
	}
	public AccessSupporterService getAccessSupporterService(){
		return service;
	}
}
