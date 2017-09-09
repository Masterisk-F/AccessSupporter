package com.poyashimitter.accesssupporter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.cgutman.adblib.AdbStream;

import org.opencv.android.Utils;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class ImageProcesser {
	Context context;
	
	File screenshotDir;
	final String screenshotPath;
	
	Mat errMatDescriptor;
	Mat connErrMatDescriptor;
	
	FeatureDetector featureDetector;
	DescriptorExtractor descriptorExtractor;
	DescriptorMatcher matcher;
	
	AdbStream adbStream;
	
	
	public static final int ERROR=1;
	public static final int CONNECTION_ERROR=2;
	public static final int NORMAL=0;
	
	public ImageProcesser(Context context){
		this.context=context;
		
		try{
			screenshotDir= new File(Environment.getExternalStorageDirectory(),context.getPackageName());
			if(!screenshotDir.exists()){
				screenshotDir.mkdir();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		screenshotPath=screenshotDir.getAbsolutePath()+"/screenshot.png";
		
		featureDetector=FeatureDetector.create(FeatureDetector.ORB);
		descriptorExtractor=DescriptorExtractor.create(DescriptorExtractor.ORB);
		matcher=DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		
		errMatDescriptor=getErrMatDescriptor();
		connErrMatDescriptor=getConnErrMatDescriptor();
	}
	
	public void setAdbStream(AdbStream stream){
		//adbstreamは非同期で生成されるため、後から渡す
		adbStream=stream;
	}
	private Mat getMatOfImage(InputStream in){
		Mat mat=new Mat();
		InputStream stream= new BufferedInputStream(in);
		Bitmap bitmap= BitmapFactory.decodeStream(stream);
		Utils.bitmapToMat(bitmap,mat);
		Log.d("accesssupporter","img loaded : rows="+mat.rows()+", cols="+mat.cols());
		
		try{
			stream.close();
		}catch(IOException e){
			e.printStackTrace();
		}
		return mat;
	}
	private Mat getErrMat(){
		try{
			return getMatOfImage(context.getAssets().open("err.jpeg"));
		}catch(IOException e){
			e.printStackTrace();
		}
		return null;
	}
	
	private Mat getConnErrMat(){
		try{
			return getMatOfImage(context.getAssets().open("conn_err.png"));
		}catch(IOException e){
			e.printStackTrace();
		}
		return null;
	}
	
	private Mat getErrMatDescriptor(){
		if(errMatDescriptor!=null){
			return errMatDescriptor;
		}
		
		Mat err=new Mat();
		Imgproc.cvtColor(getErrMat(),err,Imgproc.COLOR_RGBA2GRAY);
		MatOfKeyPoint errKeyPoint=new MatOfKeyPoint();
		featureDetector.detect(err,errKeyPoint);
		Mat errDescriptor=new Mat(err.rows(),err.cols(),err.type());
		descriptorExtractor.compute(err,errKeyPoint,errDescriptor);
		return errDescriptor;
	}
	
	private Mat getConnErrMatDescriptor(){
		if(connErrMatDescriptor!=null){
			return connErrMatDescriptor;
		}
		
		Mat connErr=new Mat();
		Imgproc.cvtColor(getConnErrMat(),connErr,Imgproc.COLOR_RGBA2GRAY);
		MatOfKeyPoint keyPoint=new MatOfKeyPoint();
		featureDetector.detect(connErr,keyPoint);
		Mat descriptor=new Mat(connErr.rows(),connErr.cols(),connErr.type());
		descriptorExtractor.compute(connErr,keyPoint,descriptor);
		return descriptor;
	}
	
	private void takeScreenshot() throws IOException{
		try{
			String command="screencap "+screenshotPath+"\n";
			adbStream.write(" "+command);
			Thread.sleep(3000);
			Log.d("accesssupporter","command = "+command);
		}catch(InterruptedException e){}
		
	}
	
	private Mat getMatOfScreenshot(){
		try{
			takeScreenshot();
			
			return getMatOfImage(new FileInputStream(screenshotPath));
		}catch(IOException e){
			e.printStackTrace();
		}
		
		return null;
	}
	
	//テスト用
	public void saveMatchesImage(){
		Mat err=new Mat();
		Imgproc.cvtColor(getConnErrMat(),err,Imgproc.COLOR_RGBA2GRAY);
		MatOfKeyPoint errKeyPoint=new MatOfKeyPoint();
		featureDetector.detect(err,errKeyPoint);
		Mat errDescriptor=new Mat(err.rows(),err.cols(),err.type());
		descriptorExtractor.compute(err,errKeyPoint,errDescriptor);
		
		Mat screen=new Mat();
		Imgproc.cvtColor(getMatOfScreenshot(),screen,Imgproc.COLOR_RGBA2GRAY);
		MatOfKeyPoint screenKeyPoint=new MatOfKeyPoint();
		featureDetector.detect(screen,screenKeyPoint);
		Mat screenDescriptor=new Mat(screen.rows(),screen.cols(),screen.type());
		descriptorExtractor.compute(screen,screenKeyPoint,screenDescriptor);
		
		MatOfDMatch matchs = new MatOfDMatch();
		DescriptorMatcher matcher=DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		matcher.match(errDescriptor,screenDescriptor,matchs);
		
		DMatch[] tmp01 = matchs.toArray();
		List<DMatch> tmp02 = new ArrayList<DMatch>();
		for (int i=0; i<tmp01.length; i++) {
			if(tmp01[i].distance<25){
				tmp02.add(tmp01[i]);
			}
		}
		matchs=new MatOfDMatch();
		matchs.fromArray(tmp02.toArray(new DMatch[tmp02.size()]));
		
		Mat matchedImage=new Mat();
		Features2d.drawMatches(err,errKeyPoint,screen,screenKeyPoint,matchs,matchedImage);
		
		Imgcodecs.imwrite(screenshotDir.getAbsolutePath()+"/match.jpg",matchedImage);
		Log.d("accesssupporter","number of matched point : "+tmp02.size());
	}
	
	public int ekimemoIsError(){
		
		//
		//saveMatchesImage();
		
		Mat screen=new Mat();
		Imgproc.cvtColor(getMatOfScreenshot(),screen,Imgproc.COLOR_RGBA2GRAY);
		long start=System.currentTimeMillis();//!!!!!!!!
		MatOfKeyPoint screenKeyPoint=new MatOfKeyPoint();
		featureDetector.detect(screen,screenKeyPoint);
		Mat screenDescriptor=new Mat(screen.rows(),screen.cols(),screen.type());
		descriptorExtractor.compute(screen,screenKeyPoint,screenDescriptor);
		
		if(screenDescriptor.rows()==0 || screenDescriptor.cols()==0){
			//スリープ時のスクショだとscreenDescriptorが空でエラーになるため
			return NORMAL;
		}
		
		MatOfDMatch matches = new MatOfDMatch();
		matcher.match(errMatDescriptor,screenDescriptor,matches);
		
		
		List<DMatch> dmList = new ArrayList<DMatch>(130);
		for (DMatch dm : matches.toArray()) {
			if(dm.distance<25){
				dmList.add(dm);
			}
		}
		//Log.d("accesssupporter","ekimemoIsError()... image processing time : "+(System.currentTimeMillis()-start)+"ms");
		Log.d("accesssupporter","ekimemoIsError()...err:dmList.size()="+dmList.size());
		
		if(dmList.size()>50)
			return ERROR;
		
		
		
		
		//conn_Err
		
		matches = new MatOfDMatch();
		matcher.match(connErrMatDescriptor,screenDescriptor,matches);
		
		
		dmList = new ArrayList<DMatch>(130);
		for (DMatch dm : matches.toArray()) {
			if(dm.distance<25){
				dmList.add(dm);
			}
		}
		
		Log.d("accesssupporter","ekimemoIsError()...conn_err:dmList.size()="+dmList.size());
		
		if(dmList.size()>50)
			return CONNECTION_ERROR;
		
		
		return NORMAL;
	}
}
