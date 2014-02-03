package com.david.ametsitsi.--;

/**
 * Created with IntelliJ IDEA.
 * User: root
 * Date: 3/23/13
 * Time: 2:37 AM
 * 
 * This is mainly a debug tool.
 * 
 * Initializes/Destroys/Pauses the preview and recording functions.
 * 
 * Saves previews to sdcard(which will be saved to a buffer in the future for analysis)
 * 
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

class CameraView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "Preview";
    private static final int resolx = 240; // Horizontal camera size
    private static final int resoly = 320; // Vertical camera size
    private static int fpsmin = 120000; // Min preview fps(scaled by 1000)
    private static int fpsmax = 120000; // Max preview fps(scaled by 1000)

    SurfaceHolder mHolder; // Declare surface holder(preview holder)
    public Camera camera; // Declare new camera

    /*
     * Called when the CameraView is created
     * 
     */
    CameraView(Context context) {
        super(context);

        //Intent AccelService = new Intent(this, Accelerometer.class);

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
    }//CameraView
    
    /*
     * Called when the application is initialized
     * 
     */
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        camera = Camera.open();
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(1280, 720); 
        camera.setParameters(parameters); // Set viewing size
        parameters.setPictureSize(resoly, resolx); //S et preview size
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO); // Set camera mode for close viewing
        //parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH); // Turn on torch (LED) for dark viewing(phone face down)
        // Get supported fps values
        List<int[]> fps = parameters.getSupportedPreviewFpsRange(); // Pull supported framerate
        int[] fpsrange = fps.remove(0); // Save fps range(int[]) taken from fps
        // If supported fps does not equal current fpsmax and fpsmin, set both to currently supported max
        if(fpsrange[1] != fpsmax){
            fpsmax = fpsrange[1];
            fpsmin = fpsrange[1]; // Intended to force constant high framerate
        }

        //parameters.setPreviewFpsRange(20, 20);

        Log.d("FPS", fpsrange[1] + "");
        camera.setParameters(parameters);
        try {
            camera.setPreviewDisplay(holder);

            camera.setPreviewCallback(new PreviewCallback() {

                public void onPreviewFrame(byte[] data, Camera arg1) {
                    FileOutputStream outStream = null;
                    try {
                        outStream = new FileOutputStream(String.format(
                                "/sdcard/%d.jpg", System.currentTimeMillis())); // Save each frame to sdcard.
                        outStream.write(data); // Write data
                        outStream.close(); // Close output stream
                        Log.d(TAG, "onPreviewFrame - wrote bytes: "
                                + data.length);// Document save in log
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                    }
                    CameraView.this.invalidate();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }//surfaceCreated

    /*
     * Called when the application exited/killed
     * 
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        // Because the CameraDevice object is not a shared resource, it's very
        // important to release it when the activity is paused.
        camera.stopPreview();
        //parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF); // Turn flash off on Kill
        camera = null;
    }//surfaceDestroyed

    /*
     * Called when the application is put on hold
     * 
     */
    public void Pause(){
        Camera.Parameters parameters = camera.getParameters(); // Pull current parameters from camera
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF); // Turn flash off on Pause(conserve energy)
        Log.d("STATE", "paused..");
    }//Pause
    
    /*
     * Called when the application is reinitialized
     * 
     */
    public void Resume(){
        camera = Camera.open(); // restart camera 
        Camera.Parameters parameters = camera.getParameters(); // Pull current parameters from camera
        //parameters.setPreviewSize(320,240); // Set preview size
        //parameters.setPictureSize(320,240); // Set picture size
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH); // reactivate Torch(LED)
        //camera.setParameters(parameters); //reset parameters
        Log.d("STATE", "resumed..");
    }//Resume
    
    /*
     * Called when surface change is detected
     * 
     */
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        camera.startPreview();
    }//surfaceChanged
    
    /*
     * Called on each save(time stamps each preview)
     * 
     */
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        Paint p = new Paint(Color.RED);
        Log.d(TAG, "draw");
        canvas.drawText(new Time(Time.getCurrentTimezone()).toString(), canvas.getWidth() / 2,
                canvas.getHeight() / 2, p); // Write the system time on each preview
    }//draw
}
