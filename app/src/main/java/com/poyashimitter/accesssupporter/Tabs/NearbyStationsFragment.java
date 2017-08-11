package com.poyashimitter.accesssupporter.Tabs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.poyashimitter.accesssupporter.AccessSupporterApplication;
import com.poyashimitter.accesssupporter.AccessSupporterService;
import com.poyashimitter.accesssupporter.StationData.Station;

import java.util.List;


public class NearbyStationsFragment extends StationsListFragment {
	/*// TODO: Rename parameter arguments, choose names that match
	// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
	private static final String ARG_PARAM1 = "param1";
	private static final String ARG_PARAM2 = "param2";
	
	// TODO: Rename and change types of parameters
	private String mParam1;
	private String mParam2;
	
	private OnFragmentInteractionListener mListener;
	*/
	
	BroadcastReceiver locationChangedReceiver=new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			setStationsList();
		}
	};
	
	public NearbyStationsFragment() {
		// Required empty public constructor
	}
	
	/**
	 * Use this factory method to create a new instance of
	 * this fragment using the provided parameters.
	 *
	 * @param param1 Parameter 1.
	 * @param param2 Parameter 2.
	 * @return A new instance of fragment NearbyStationsFragment.
	 */
	// TODO: Rename and change types and number of parameters
	public static NearbyStationsFragment newInstance(String param1, String param2) {
		NearbyStationsFragment fragment = new NearbyStationsFragment();
		Bundle args = new Bundle();
		args.putString(ARG_PARAM1, param1);
		args.putString(ARG_PARAM2, param2);
		fragment.setArguments(args);
		return fragment;
	}
	
	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		if((((AccessSupporterApplication)getActivity().getApplication()).getAccessSupporterService().isRunning())){
			setStationsList();
		}
		
		IntentFilter filter=new IntentFilter();
		filter.addAction(AccessSupporterService.LOCATION_CANGED);
		getActivity().registerReceiver(locationChangedReceiver,filter);
	}
	
	void setStationsList(){
		AccessSupporterApplication application=(AccessSupporterApplication)getActivity().getApplication();
		if(application==null){
			return;
		}
		Location location=application.getCurrentLocation();
		
		if(location==null ||application.getStationHandler()==null){
			return;
		}
		
		List<Station> list=application.getStationHandler().getNearbyStationsList(location);
		
		setStationsList(list,true);
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
		getActivity().unregisterReceiver(locationChangedReceiver);
	}
	
}
