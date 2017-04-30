package com.poyashimitter.accesssupporter.StationData;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Tatu on 2016/10/14.
 */

/*
* 同じ駅かどうかの判定は、「駅グループコードが同じ かつ 駅名が同じ」
* 
* 駅コードと駅グループコードとが異なる場合は、同じ駅が既に存在している場合がある
* */

public class Station {
	private List<Integer> station_cd;//駅コード、あまり使わないがとりあえず
	private int station_g_cd;//駅グループコード
	
	private String station_name;//駅名
	
	private List<Line> line;//所属路線
	
	private String address;//住所
	
	private double lon;//経度
	private double lat;//緯度
	
	public Station(){
		station_cd=new ArrayList<Integer>(1);
		line=new ArrayList<Line>(1);
	}
	
	public void addStationCode(int cd){
		station_cd.add(cd);
	}
	public int[] getStationCodes(){
		int[] arr=new int[station_cd.size()];
		for(int i=0;i<station_cd.size();i++){
			arr[i]=station_cd.get(i);
		}
		return arr;
	}
	public void setStationGroupCode(int cd){
		station_g_cd=cd;
	}
	public int getStationGroupCode(){
		return station_g_cd;
	}
	public void setStationName(String name){
		station_name=name;
	}
	public String getStationName(){
		return station_name;
	}
	public void addLine(Line l){
		line.add(l);
		
	}
	public Line[] getLines(){
		return line.toArray(new Line[line.size()]);
	}
	public String getLineNames(){
		String str="";
		for(Line l:line){
			str+=l.getLineName()+", ";
		}
		return str;
	}
	
	public void setAddress(String add){
		address=add;
	}
	public String getAddress(){
		return address;
	}
	
	public void setLongitude(double l){
		lon=l;
	}
	public double getLongitude(){
		return lon;
	}
	public void setLatitude(double l){
		lat=l;
	}
	public double getLatitude(){
		return lat;
	}
	/*
	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof Station))
			return false;
		
		Station sta=(Station)obj;
		
		return station_g_cd==sta.station_g_cd && station_name.equals(sta.station_name);
	}
	*/
	public void merge(Station sta){
		//同じ駅staの情報をこのインスタンスに追加
		station_cd.addAll(sta.station_cd);
		line.addAll(sta.line);
	}
	public void merge(int station_cd,Line line){
		this.station_cd.add(station_cd);
		this.line.add(line);
	}
	
	public double distanceTo(double longitude, double latitude){
		//参考 : http://keisan.casio.jp/exec/system/1257670779
		/*
		double r=6371000;//地球の平均半径[m]
		double rad=2*Math.PI/360;
		return r*Math.acos(
				Math.sin(this.lat*rad)*Math.sin(latitude*rad)
				+Math.cos(this.lat*rad)*Math.cos(latitude*rad)*Math.cos((longitude-this.lon)*rad)
				);
		*/
		
		
		double lonDis=distanceOfLongitude(this.lon-longitude,latitude);
		double latDis=distanceOfLatitude(this.lat-latitude);
		return Math.sqrt(lonDis*lonDis+latDis*latDis);
		
	}
	
	private double distanceOfLongitude(double deltaLon,double latitude){
		/*
		//参考 : https://ja.wikipedia.org/wiki/%E7%B5%8C%E5%BA%A6
		double a = 6378137;
		double e2 = 0.006694380022900788;
		double rad=2*Math.PI/360;
		return a*Math.cos(latitude*rad)/Math.sqrt(1-e2*Math.sin(latitude*rad)*Math.sin(latitude*rad))*deltaLon;
		*/
		
		double r=6371000;
		//double rad=2*Math.PI/360;
		//return 2*Math.PI*r*Math.cos(latitude*rad)*deltaLon/360;
		return 2*Math.PI*r*deltaLon/360;
	}
	
	private double distanceOfLatitude(double deltaLat){
		
		
		double r=6371000;
		return 2*Math.PI*r*deltaLat/360;
		
	}
}