package com.poyashimitter.accesssupporter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

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
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class ImageProcesser {
	Context context;
	
	final String screenshotPath;
	
	Mat errMatDescriptor;
	
	FeatureDetector featureDetector;
	DescriptorExtractor descriptorExtractor;
	DescriptorMatcher matcher;
	
	public ImageProcesser(Context context){
		this.context=context;
		
		screenshotPath="/sdcard/"+context.getPackageName()+"/screenshot.png";
		
		featureDetector=FeatureDetector.create(FeatureDetector.ORB);
		descriptorExtractor=DescriptorExtractor.create(DescriptorExtractor.ORB);
		matcher=DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
		
		errMatDescriptor=getErrMatDescriptor();
	}
	
	private Mat getErrMat(){
		
		Mat errMat=new Mat();
		InputStream stream=null;
		try{
			stream= new BufferedInputStream(context.getAssets().open("err.jpeg"));
			Bitmap bitmap= BitmapFactory.decodeStream(stream);
			Utils.bitmapToMat(bitmap,errMat);
			Log.d("accesssupporter","img loaded : rows="+errMat.rows()+", cols="+errMat.cols());
		}catch(IOException e){
			e.printStackTrace();
		}finally{
			if(stream!=null){
				try{
					stream.close();
				}catch(IOException e){
					e.printStackTrace();
				}
			}
		}
		
		return errMat;
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
	
	private void takeScreenshot() throws IOException{
		String command="adb shell screencap "+screenshotPath;
		Process process=Runtime.getRuntime().exec(command+" 2>&1");
		int i=1111;
		
		BufferedReader is=new BufferedReader(new InputStreamReader(process.getInputStream()));
		String br;
		StringBuffer sb=new StringBuffer();
		while((br=is.readLine())!=null){
		sb.append(br+"\n");
		}
		try{
			i=process.waitFor();
		}catch(InterruptedException e){
			e.printStackTrace();
		}
		Log.d("accesssupporter","command = "+command+" , i="+i+"\n"+sb);
		
	}
	
	private Mat getMatOfScreenshot(){
		InputStream stream=null;
		Mat mat=new Mat();
		try{
			takeScreenshot();
			
			stream =new BufferedInputStream(new FileInputStream(screenshotPath));
			Bitmap bitmap = BitmapFactory.decodeStream(stream);
			Utils.bitmapToMat(bitmap,mat);
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}finally{
			if(stream!=null){
				try{
					stream.close();
				}catch(IOException e){
					e.printStackTrace();
				}
			}
		}
		return mat;
	}
	
	public void saveMatchesImage(){
		Mat err=new Mat();
		Imgproc.cvtColor(getErrMat(),err,Imgproc.COLOR_RGBA2GRAY);
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
			Log.d("accesssupporter","tmp["+i+"].distance = "+tmp01[i].distance);
			if(tmp01[i].distance<15){
				tmp02.add(tmp01[i]);
			}
			//tmp02[i] = tmp01[i];
		}
		matchs=new MatOfDMatch();
		matchs.fromArray(tmp02.toArray(new DMatch[tmp02.size()]));
		
		Mat matchedImage=new Mat(/*screen.rows(),err.cols()+screen.cols(),screen.type()*/);
		Features2d.drawMatches(err,errKeyPoint,screen,screenKeyPoint,matchs,matchedImage);
		
		Imgcodecs.imwrite("/sdcard/"+context.getPackageName()+"/match.jpg",matchedImage);
		Log.d("accesssupporter","number of matched point : "+tmp02.size());
	}
	
	public boolean ekimemoIsError(){
		Mat screen=new Mat();
		Imgproc.cvtColor(getMatOfScreenshot(),screen,Imgproc.COLOR_RGBA2GRAY);
		long start=System.currentTimeMillis();//!!!!!!!!
		MatOfKeyPoint screenKeyPoint=new MatOfKeyPoint();
		featureDetector.detect(screen,screenKeyPoint);
		Mat screenDescriptor=new Mat(screen.rows(),screen.cols(),screen.type());
		descriptorExtractor.compute(screen,screenKeyPoint,screenDescriptor);
		
		if(screenDescriptor.rows()==0 || screenDescriptor.cols()==0){
			//スリープ時のスクショだとscreenDescriptorが空でエラーになるため
			return false;
		}
		
		MatOfDMatch matches = new MatOfDMatch();
		Log.d("accesssupporter","errMatDescriptor : "+errMatDescriptor.toString()+"\nscreenDescriptor : "+screenDescriptor.toString());//!!!!!!!!!!!!!!
		try{
			matcher.match(errMatDescriptor,screenDescriptor,matches);
		}catch(Exception e){//スリープ時のスクショだとscreenDescriptorが空でエラーになるため
			e.printStackTrace();
			return false;
		}
		
		List<DMatch> dmList = new ArrayList<DMatch>(130);
		for (DMatch dm : matches.toArray()) {
			//Log.d("accesssupporter","dm.distance = "+dm.distance);
			if(dm.distance<15){
				dmList.add(dm);
			}
		}
		Log.d("accesssupporter","ekimemoIsError()... image processing time : "+(System.currentTimeMillis()-start)+"ms");
		return dmList.size()>50;
	}
}
