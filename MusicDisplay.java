package com.daametsi.main;

import java.text.DecimalFormat;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.XYPlot;
import com.daametsi.main.R;
import android.app.Activity;
import android.content.Context;

import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;


import android.graphics.Color;
import android.graphics.PointF;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TextView;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class MusicDisplay extends Activity implements Runnable, OnTouchListener {
	
	private static final String TAG = "coord";// Output for debugging coordinate data

	
	PowerManager.WakeLock wl; // Declare wake lock to keep screen on during recording
	
	GraphFrequency freqSeries; // Declare data series
	private XYPlot dynamicPlot; // Declare dynamic plot
	private PlotUpdater plotUpdater; // Declare plot updater

	private Notifier notifier; // Declare plot notifier 

	private Thread freqCalc;
	public static final int nyquistFreq = 22050; // Initialize nuquist frequency = max frequency
	public static final int samplrate = nyquistFreq*2; //Initialize sampling rate
	public static final int channelConf = AudioFormat.CHANNEL_IN_STEREO; // store channel mode into the config variable
	int numPoints = 200;//number of graphed points * 2
	//divideBy = total points/graphed points(used for information on the ratio of plotted points and actual points)
	//minBuffer = minimum possible size of buffer(pulled directly from each device)
	//domainMax = max plotable frequency
	//avg = average magnitude accross all frequencies(this is used to ignore interfereance; taps, other indescriminate overloads, etc.)
	int divideBy, minBuffer, domainMax,avg;
	short[] audioBuffer; // Declare audioBuffer to buffer the mic input
	//freq = holds
	//doubBuffer = 
	//frequency = 
	double[] freq, doubBuffer, frequency;
	DoubleFFT_1D doub; // Declare Complex/Real Fast Fourier Transform
	//RealDoubleFFT doub;


	String frequencies; // Hold max frequencies
	TextView currNote; // Output current pitch as a letter A-F
	//Button btnstart;


	//optimizing = keep track of the optimization process
	//recording = keep track of recording state
	boolean optimizing, recording;
	//private XYPlot aprHistoryPlot = null;

	AudioRecord recorder; // Declare Audio Recorder

	/*
	 * Called when the activity is first created. 
	 *
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, ""); // keep the screen on but dimmed mode

		dynamicPlot = (XYPlot) findViewById(R.id.freqPlot); // Associate xml layout to dynamicPlot
		plotUpdater = new MyPlotUpdater(dynamicPlot); // Attach Plotupated to dynamicPlot
		// hook up the plotUpdater to the data model:
		this.addObserver(plotUpdater); // initialize plotUpdater as an Observer
		dynamicPlot.setOnTouchListener(this); // Attach touch listening to graph area

		// only display whole numbers in domain labels
		dynamicPlot.getGraphWidget().setDomainValueFormat(new DecimalFormat("0"));
		dynamicPlot.setGridPadding(5, 0, 5, 0);
		//dynamicPlot.setDomainStepMode(XYStepMode.SUBDIVIDE);
		//dynamicPlot.setDomainStepValue(10);
		// thin out domain/range tick labels so they dont overlap each other:
		//dynamicPlot.setTicksPerDomainLabel(5);
		//dynamicPlot.setTicksPerRangeLabel(3);
		dynamicPlot.disableAllMarkup();
		//dynamicPlot.getAnimation();
		dynamicPlot.setDomainLabel("Frequency"); 
		dynamicPlot.setRangeLabel("Magnitude");
		// freeze the range boundaries:
		dynamicPlot.setRangeBoundaries(-20, 130, BoundaryMode.FIXED); // Set verticle boundaries(magnitude/dB)
		/*dynamicPlot.setDomainLabelWidget(new DomainLabelWidget(dynamicPlot,
						new SizeMetrics((float) 280,
		  				SizeLayoutType.FILL, 
		  				(float) nyquistFreq,
		  				SizeLayoutType.FILL),
		  				TextOrientationType.HORIZONTAL));*/

		//freeze the domain boundaries:
		//dynamicPlot.setDomainBoundaries(0, nyquistFreq, BoundaryMode.FIXED);

		/*btnstart = (Button) findViewById(R.id.startRecord);*/
		currNote = (TextView) findViewById(R.id.NoteView01); // Add noteview to layout

		minBuffer = AudioRecord.getMinBufferSize(samplrate, channelConf, AudioFormat.ENCODING_PCM_16BIT);//16384 for Samsung Captivate

		domainMax = nyquistFreq; // Set domainMax to nyquist frequency

		try{
			//doub = new RealDoubleFFT(minBuffer);
			doub = new DoubleFFT_1D(minBuffer); // Initialize double FFT, catch use default value
		} catch(final ArrayIndexOutOfBoundsException e) {
			minBuffer = 16384; // For samsung Captivate
			//doub = new RealDoubleFFT(minBuffer);
			doub = new DoubleFFT_1D(minBuffer);
		}
		divideBy = minBuffer/numPoints; // Define divideBy


		freq = new double[3];// max amp,max freq, total avg
		doubBuffer = new double[minBuffer];//16384 for Samsung Captivate
		//frequency = new double[minBuffer/divideBy+1];
		frequency = new double[(minBuffer/divideBy+1)/2]; // Initialize amount of graphed data

		audioBuffer = new short[minBuffer]; // Initialize buffer size

		//MusicTranscriberActivity data = new MusicTranscriberActivity();
		freqSeries = new GraphFrequency(frequency, "Frequency"); // Initialize graph data

		// create a series using a temporary formatter, with a transparent fill applied immediately
		dynamicPlot.addSeries(freqSeries, new LineAndPointFormatter(Color.TRANSPARENT, Color.argb(200, 65, 105, 225), Color.TRANSPARENT));
	
		// Initialize frequencies to none
		frequencies ="None";

		// Initialize this Activity as a Runnable Thread
		freqCalc= new Thread(this);

		//btnstart.setText("StartX"); // Initialize start button text (commented because recording starts automatically for debugging convenience)
	
		// Create audio recorder according to the specified information; audio channel, buffer size, etc.
		recorder = new AudioRecord(AudioSource.MIC, samplrate, channelConf, AudioFormat.ENCODING_PCM_16BIT, minBuffer);
	
		dynamicPlot.redraw(); // update plot

		//Set of internal variables for keeping track of the boundaries
		dynamicPlot.calculateMinMaxVals();
		minXY = new PointF(dynamicPlot.getCalculatedMinX().floatValue(),
				dynamicPlot.getCalculatedMinY().floatValue()); //initial minimum data point
		absMinX = minXY.x; //absolute minimum data point
		//absolute minimum value for the domain boundary maximum
		minNoError = Math.round(freqSeries.getX(1).floatValue() + 2);
		maxXY = new PointF(dynamicPlot.getCalculatedMaxX().floatValue(),
				dynamicPlot.getCalculatedMaxY().floatValue()); //initial maximum data point
		absMaxX = maxXY.x; //absolute maximum data point
		//absolute maximum value for the domain boundary minimum
		maxNoError = (float) Math.round(freqSeries.getX(freqSeries.size() - 1).floatValue()) - 2;
	
		//Check x data to find the minimum difference between two neighboring domain values
		//Will use to prevent zooming further in than this distance
		double temp1 = freqSeries.getX(0).doubleValue();
		double temp2 = freqSeries.getX(1).doubleValue();
		double temp3;
		double thisDif;
		minDif = 1000000;	//increase if necessary for domain values
		for (int i = 2; i < freqSeries.size(); i++) {
			temp3 = freqSeries.getX(i).doubleValue();
			thisDif = Math.abs(temp1 - temp3);
			if (thisDif < minDif)
				minDif = thisDif;
			temp1 = temp2;
			temp2 = temp3;
		}
		minDif = minDif + difPadding; //with padding, the minimum difference

		// Set recording state to true
		recording = true;
		// Set optimizing state to true for first run
		optimizing = true;
		if(freqCalc.getState() != Thread.State.NEW)
			freqCalc.stop(); // If running, stop..
		freqCalc.start(); // Start embedded thread
	
	}//onCreate

	/*
	 * Called when the button is pressed
	 *
	 */
	/*public void strthandler(View v){
	switch (v.getId()) {
		case R.id.startRecord: // doStuff
	  		if(!recording) {
	  			btnstart.setVisibility(Button.INVISIBLE); // Remove button or..
	  			//btnstart.setText("Stop"); // Set text to Stop
	    			recording = true; // Set recording to true
	    			if(freqCalc.getState() != Thread.State.NEW)
	    				freqCalc.stop();
	  		} else {
	  			btnstart.setText("Start"); // Set button Text back to Start
	  			recording = false; // Set recording to false
	  		}
				break;
		// case R.id.stopRecord: // Make stop button appear..
	}//switch
	}//strthandler
	*/    

	{
	notifier = new Notifier(); // Intialize graph notifier
	}

	/*
	 * Called when the application Thread is started
	 *
	 */
	public void run() {
		while(recording){
			wl.acquire();//start wake lock
	
			//if(recorder.getState() == AudioRecord.STATE_INITIALIZED)
	
			frequencies = "None"; // Initialize max frequencies to "None"
	
			if(recorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
		      		recorder.startRecording(); // Start if audio recording has stopped
			}
	
			//long startCalc = System.currentTimeMillis();
				recorder.read(audioBuffer, 0, minBuffer);
				for(int i = 0; i < minBuffer; i++) {
					doubBuffer[i] = (double) audioBuffer[i]; // move audioBuffer data and into doubBuffer
				}
		
		
				//Log.v(TAG, "FFT calc"); // document FFT calc begin
				//doub.ft(doubBuffer);
				//doub.realInverse(doubBuffer, false);
				//doub.ft(doubBuffer);
				//doub.bt(doubBuffer);
				//doub.bt(doubBuffer);
				doub.realForward(doubBuffer); // Real Forward FFT
				//doub.realInverseFull(doubBuffer, true);
		
				for(int i = 2; i < doubBuffer.length/2; i++) {
					//frequency[(j/divideBy)*(frequency.length/doubBuffer.length)] = (Math.sqrt(Math.pow(doubBuffer[j],2) + Math.pow(doubBuffer[j+1],2)))/minBuffer;
					if(i % divideBy == 0)
						frequency[(i/(divideBy))-1] = (Math.sqrt(Math.pow(doubBuffer[i],2) + Math.pow(doubBuffer[i+1],2)))/minBuffer; // Calculate frequencies from DDR
			
					freq[2]+=doubBuffer[i]; // Sum all magnitudes for average
				}//for
				freq[2]/=(doubBuffer.length); // Determine avg value
				//long finishCalc = System.currentTimeMillis() - startCalc;
		
				freq = findMax(frequency); // find the max Frequencies and report them onto the display
		
				runOnUiThread(new Runnable() {
				    public void run() {
				    	currNote.setText("" + divideBy + 
				    			" amp:" + (int) freq[0] + 
				    			" avg:" + (int) freq[2] + 
				    			" max freq:" + (int) ((freq[1]*domainMax)/frequency.length) +
				    			/*" freq:" + frequencies +*/
				    			" touch sensor:" + mode); // Display debug data
				    }
				});//runOnUiThread
		
		
				// Optimizing ensures that enough points are plotted by incrementing until the number of points causes a delay greater than 40ms
				if(optimizing) {
		  			long startDraw = System.currentTimeMillis();
		  			notifier.notifyObservers();
		  			long finishDraw = System.currentTimeMillis() - startDraw; // Record draw time
		  			if(finishDraw < 40) {
		  				numPoints+=30;
		  				divideBy = minBuffer/numPoints;
		  				frequency = new double[(minBuffer/divideBy+1)/2];
		  				dynamicPlot.removeSeries(freqSeries);
		  				freqSeries = new GraphFrequency(frequency, "Frequency");
		  				dynamicPlot.addSeries(freqSeries, new LineAndPointFormatter(Color.TRANSPARENT, Color.argb(200, 65, 105, 225), Color.argb(32, 178, 170, 225)));
		  				dynamicPlot.setDomainBoundaries(0, frequency.length, BoundaryMode.FIXED);
		  			} else//if
						// stop optimizing when finishDraw > 40ms
		  				optimizing = false;//else
				} else //if
					// Redraw graph
					notifier.notifyObservers();//else
		}//while
		if (recorder.getState()==android.media.AudioRecord.RECORDSTATE_RECORDING)
			recorder.stop(); //stop the recorder before ending the thread
		recorder.release(); //release the recorder data
		wl.release(); // Release wakelock after recording has terminated
		recorder=null; // purge recorder
	}//run
	
	/*
	 * Used to find max, peak and prodominant magnitudes
	 *
	 */
	private double[] findMax(double[] arr1) {
		frequencies = ""; // Purge data and reinitialize
		double[] max = {arr1[1], 1, arr1[2]}; //max value,max value index,
	
		for(int i = 1;i < arr1.length; i++) {
				//if(arr1[i] > avg + 4) {
					if(arr1[i] > max[0]){
						max[0] = arr1[i]; // If arr1[i] is greater than current max update max..
						max[1] = i;	//and save index(frequency value)
					}
					if(arr1[i] > (avg+30)) // Log all max frequencies that pass a tolerance above the avg magnitude value..
						frequencies += " " + (int) ((i*2*domainMax)/frequency.length);
				//}
		
		}
		return max;
	}
	
	/*
	 * Called when Observer must be added
	 *
	 */
	public void addObserver(Observer observer) {
		notifier.addObserver(observer);
	}//addObserver

	/*
	 * Called when Observer must be removed
	 *
	 */
	public void removeObserver(Observer observer) {
		notifier.deleteObserver(observer);
	}//removeObserver

}
