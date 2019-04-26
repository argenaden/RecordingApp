package com.example.testingjavacv;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.Camera;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;


import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


import pub.devrel.easypermissions.EasyPermissions;


public class MainActivity extends AppCompatActivity {



    private final static String LOG_TAG = "MainActivity";

    // The minimum distance to change Updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1; //10*1 10 meters

    // The minimum time between updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = 1000 * 1; // 1 second

    private PowerManager.WakeLock mWakeLock; //to stay on
    private static int BUFFER_SIZE = 6 * 1024;

    private volatile FFmpegFrameRecorder recorder;

    boolean recording = false;
    boolean finished = false;
    private boolean startRecordAgain = true;
    static long startTime = 0;
    static long endTime;
    private String todays_date = DateFormat.getDateTimeInstance().format(new Date());

    private int counter = 0;
    static int p = 0;
    private final int REQUEST_CODE = 2;
    private int sampleAudioRateInHz = 44100;
    private int imageWidth = 320;
    private int imageHeight = 240;
    private int frameRate = 30;

    private Thread audioThread;
    volatile boolean runAudioThread = true;
    private AudioRecord audioRecord;
    private AudioRecordRunnable audioRecordRunnable;
    private  String result;
    private CameraView cameraView;
    private Frame yuvImage;

    private Button recordButton;
    private LinearLayout mainLayout;
    private boolean init = false;
    LocationService myService;
    static boolean status;
    LocationManager locationManager;
    static ArrayList<Integer> speed = new ArrayList<>();
    @SuppressLint("UseSparseArrays")
    HashMap<String, Integer> speed_info = new HashMap<String, Integer>();

    long millis_startTime;

    private ServiceConnection sc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationService.LocalBinder binder = (LocationService.LocalBinder) service;
            myService = binder.getService();
            status = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            status = false;
        }
    };

    void bindService() {
        if (status == true)
            return;
        Intent i = new Intent(getApplicationContext(), LocationService.class);
        bindService(i, sc, BIND_AUTO_CREATE);
        status = true;
        startTime = System.currentTimeMillis();
    }

    void unbindService() {
        if (status == false)
            return;
        Intent i = new Intent(getApplicationContext(), LocationService.class);
        unbindService(sc);
        status = false;
    }

    @Override
    public void onBackPressed() {
        if (status == false)
            super.onBackPressed();
        else
            moveTaskToBack(true);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        millis_startTime = System.currentTimeMillis();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_CODE){
            if(resultCode == RESULT_OK){
                //result = data.getStringExtra("returnData");
            }
        }
    }

    @SuppressLint("InvalidWakeLockTag")
    @Override
    protected void onResume() {
        super.onResume();

        boolean hasPermissions = EasyPermissions.hasPermissions(this, Manifest.permission.CAMERA)
                && EasyPermissions.hasPermissions(this, Manifest.permission.RECORD_AUDIO)
                && EasyPermissions.hasPermissions(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                && EasyPermissions.hasPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION)
                && EasyPermissions.hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (hasPermissions) {
            if (!init) {
                initLayout();
                init = true;
            }
        } else {
            Toast.makeText(this, "Please, allow all permissions in app settings", Toast.LENGTH_LONG).show();
        }

        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, LOG_TAG);
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (status == true)
            unbindService();
        recording = false;
        finished = true;
    }

    private void initLayout() {

        mainLayout = (LinearLayout) this.findViewById(R.id.record_layout);
        //mainLayout = (LinearLayout) this.findViewById(R.id.recorder);

        recordButton = (Button) findViewById(R.id.recorder_control);
        recordButton.setText(R.string.start_recoding);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!recording) {
                    startRecord();
                    Log.w(LOG_TAG, "Start Button Pushed");
                    recordButton.setText("Stop");
                } else {
                    stopRecord();
                    Log.w(LOG_TAG, "Stop Button Pushed");
                    recordButton.setText("Start");
                }
            }
        });

        cameraView = new CameraView(this);

        LinearLayout.LayoutParams layoutParam = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mainLayout.addView(cameraView, layoutParam);
        Log.v(LOG_TAG, "added cameraView to mainLayout");
    }

    private void initRecorder() {
        String ffmpeg_link = Environment.getExternalStorageDirectory() + "/video" + counter + ".flv";
        Log.w(LOG_TAG,"initRecorder");


        yuvImage = new Frame(imageWidth, imageHeight, Frame.DEPTH_UBYTE, 2);
        // Log.d(LOG_TAG, "IplImage.create");

        recorder = new FFmpegFrameRecorder(ffmpeg_link, imageWidth, imageHeight, 1);
        // Log.v(LOG_TAG, "FFmpegFrameRecorder: " + ffmpeg_link + " imageWidth: " + imageWidth + " imageHeight " + imageHeight);

        recorder.setFormat("flv");
        // Log.v(LOG_TAG, "recorder.setFormat(\"flv\")");

        recorder.setSampleRate(sampleAudioRateInHz);
        // Log.v(LOG_TAG, "recorder.setSampleRate(sampleAudioRateInHz)");

        // re-set in the surface changed method as well
        recorder.setFrameRate(frameRate);
        // Log.v(LOG_TAG, "recorder.setFrameRate(frameRate)");


        // Create audio recording thread
        audioRecordRunnable = new AudioRecordRunnable();
        // audioRecordRunnable.start();
        audioThread = new Thread(audioRecordRunnable);
    }




    // Start the capture
    public void startRecord() {

        calculateSpeedFromGps();
        initRecorder();
        final String n = SelectionActivity.login_name;

        try {
            recorder.start();
            startTime = System.currentTimeMillis();
            recording = true;
            audioThread.start();

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopRecord();
                    Log.w(LOG_TAG, "Stop Button Pushed");
                    recordButton.setText("Start");

                    try {

                        //csv file that contains speed information
                        File a = new File(Environment.getExternalStorageDirectory(), n + "_speed.csv");
                        FileOutputStream fileOut = new FileOutputStream(a);
                        OutputStreamWriter outputWriter = new OutputStreamWriter(fileOut);
                        outputWriter.write(speed_info.toString());
                        outputWriter.flush();
                        fileOut.getFD().sync();
                        outputWriter.close();

                        //folder that contains all files in zip
                        String backupDBPath = Environment.getExternalStorageDirectory().getPath() + "/UploadToServer";
                        final File backupDBFolder = new File(backupDBPath);
                        backupDBFolder.mkdirs();

                        String[] s = new String[2];

                        //one zip contains speed and one video
                        final File backupD1 = new File(Environment.getExternalStorageDirectory(), "/video"+ (counter) + ".flv");
                        s[0] = backupD1.getAbsolutePath();
                        s[1] = a.getAbsolutePath();
                        zip(s, backupDBPath + "/" + DateFormat.getDateTimeInstance().format(new Date()) + ".zip");


                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    for (int spd : speed) {
                        speed_info.put(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()) ,spd);
                        Log.w(LOG_TAG, "current speed: " +  spd + "  km/hr");
                        if (spd > 0) {
                            startRecordAgain = false;

                            break;
                        }
                    }

                    if (startRecordAgain) {
                        Log.w(LOG_TAG, "Start Button Pushed");
                        recordButton.setText("Stop");
                        speed = new ArrayList<>();
                        counter++;
                        startRecord();

                    }

                }
            },   10000);

        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    private void calculateSpeedFromGps() {
        checkGps();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return;
        }
        if (status == false)
            bindService();
    }

    public void stopRecord() {
        // This should stop the audio thread from running
        runAudioThread = false;



        if (recorder != null && recording) {
            recording = false;
            Log.v(LOG_TAG,"Finishing recording, calling stop and release on recorder");
            try {
                recorder.stop();
                recorder.release();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;
        }

        if (status == true)
            unbindService();
        p = 0;
    }

    public void zip(String[] files, String zipFile) throws IOException {
        BufferedInputStream origin = null;
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
        try {
            byte data[] = new byte[BUFFER_SIZE];

            for (int i = 0; i < files.length; i++) {
                FileInputStream fi = new FileInputStream(files[i]);
                origin = new BufferedInputStream(fi, BUFFER_SIZE);
                try {
                    ZipEntry entry = new ZipEntry(files[i].substring(files[i].lastIndexOf("/") + 1));
                    out.putNextEntry(entry);
                    int count;
                    while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1) {
                        out.write(data, 0, count);
                    }
                } finally {
                    origin.close();
                }
            }
        } finally {
            out.close();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Quit when back button is pushed
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (recording) {
                stopRecord();
            }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    //---------------------------------------------
    // audio thread, gets and encodes audio data
    //---------------------------------------------



    class AudioRecordRunnable extends Thread {

        @Override
        public void run() {
            // Set the thread priority
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            // Audio
            int bufferSize;
            short[] audioData;
            int bufferReadResult;

            bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            audioData = new short[bufferSize];

            Log.d(LOG_TAG, "audioRecord.startRecord()");
            audioRecord.startRecording();

            // Audio Capture/Encoding Loop
            while (runAudioThread) {
                // Read from audioRecord
                bufferReadResult = audioRecord.read(audioData, 0, audioData.length);
                if (bufferReadResult > 0) {
                    //Log.v(LOG_TAG,"audioRecord bufferReadResult: " + bufferReadResult);

                    // Changes in this variable may not be picked up despite it being "volatile"
                    if (recording) {
                        try {
                            // Write to FFmpegFrameRecorder
                            recorder.recordSamples(ShortBuffer.wrap(audioData, 0, bufferReadResult));
                        } catch (FFmpegFrameRecorder.Exception e) {
                            Log.e(LOG_TAG, e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
            Log.v(LOG_TAG,"AudioThread Finished");

            /* Capture/Encoding finished, release recorder */
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.v(LOG_TAG,"audioRecord released");
            }
        }
    }





    class CameraView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {

        private boolean previewRunning = false;

        private SurfaceHolder holder;
        private Camera camera;

        private byte[] previewBuffer;

        long videoTimestamp = 0;

        Bitmap bitmap;
        Canvas canvas;

        public CameraView(Context _context) {
            super(_context);

            holder = this.getHolder();
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);

            try {
                camera.setPreviewDisplay(holder);
                camera.setPreviewCallback(this);

                Camera.Parameters currentParams = camera.getParameters();
                //  Log.v(LOG_TAG,"Preview Framerate: " + currentParams.getPreviewFrameRate());
                //  Log.v(LOG_TAG,"Preview imageWidth: " + currentParams.getPreviewSize().width + " imageHeight: " + currentParams.getPreviewSize().height);

                // Use these values
                imageWidth = currentParams.getPreviewSize().width;
                imageHeight = currentParams.getPreviewSize().height;
                frameRate = currentParams.getPreviewFrameRate();

                bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ALPHA_8);



                camera.startPreview();
                previewRunning = true;
            }
            catch (IOException e) {
                Log.v(LOG_TAG,e.getMessage());
                e.printStackTrace();
            }
        }

        //public void surfaceChanged(SurfaceHolder holder, int format, int width, int height);
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            //   Log.v(LOG_TAG,"Surface Changed: width " + width + " height: " + height);



            // Get the current parameters
            Camera.Parameters currentParams = camera.getParameters();
            // Log.v(LOG_TAG,"Preview Framerate: " + currentParams.getPreviewFrameRate());
            //Log.v(LOG_TAG,"Preview imageWidth: " + currentParams.getPreviewSize().width + " imageHeight: " + currentParams.getPreviewSize().height);

            // Use these values
            imageWidth = currentParams.getPreviewSize().width;
            imageHeight = currentParams.getPreviewSize().height;
            frameRate = currentParams.getPreviewFrameRate();


            yuvImage = new Frame(imageWidth, imageHeight, Frame.DEPTH_UBYTE, 2);

        }


        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                camera.setPreviewCallback(null);

                previewRunning = false;
                camera.release();

            } catch (RuntimeException e) {
                Log.v(LOG_TAG,e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {

            if (yuvImage != null && recording) {
                videoTimestamp = 1000 * (System.currentTimeMillis() - startTime);

                // Put the camera preview frame right into the yuvIplimage object
                //yuvIplimage.getByteBuffer().put(data);

                // region
                ((ByteBuffer)yuvImage.image[0].position(0)).put(data);
                // endregion


                try {

                    // Get the correct time
                    recorder.setTimestamp(videoTimestamp);

                    // Record the image into FFmpegFrameRecorder
                    //recorder.record(yuvIplimage);

                    // region
                    recorder.record(yuvImage);
                    // endregion

                } catch (FFmpegFrameRecorder.Exception e) {
                    Log.v(LOG_TAG,e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }


    void checkGps() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {


            showGPSDisabledAlertToUser();
        }
    }

    private void showGPSDisabledAlertToUser() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage("Enable GPS to use application")
                .setCancelable(false)
                .setPositiveButton("Enable GPS",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent callGPSSettingIntent = new Intent(
                                        android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(callGPSSettingIntent);
                            }
                        });
        alertDialogBuilder.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }

}
