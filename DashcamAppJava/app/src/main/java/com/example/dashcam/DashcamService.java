package com.example.dashcam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.LocationManager;
import android.location.LocationListener;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class DashcamService extends Service {

    private static final String CHANNEL_ID = "DashcamChannel";
    private static final int MAX_FILES = 20;
    private static final int CLIP_DURATION_MS = 3 * 60 * 1000;

    private MediaRecorder mediaRecorder;
    private Handler handler;
    private Runnable loopTask;
    private String currentLocationText = "GPS: --,--";

    private LocationManager locationManager;
    private LocationListener locationListener;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification("Recording in progress..."));

        initLocationUpdates();
        handler = new Handler();
        startNewClip();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Dashcam Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Dashcam Running")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }

    private void initLocationUpdates() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = location -> {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            currentLocationText = String.format(Locale.getDefault(),
                    "GPS: %.5f, %.5f", lat, lon);
        };

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000,
                    5,
                    locationListener
            );
        } catch (SecurityException e) {
            Log.e("DashcamService", "GPS permission missing", e);
        }
    }

    private void startNewClip() {
        stopRecording();

        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Dashcam");
        if (!dir.exists()) dir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File file = new File(dir, "dashcam_" + timestamp + "_" + currentLocationText.replace(" ", "_").replace(",", "_") + ".mp4");

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(file.getAbsolutePath());
        mediaRecorder.setVideoEncodingBitRate(5 * 1000 * 1000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(1280, 720);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            Log.d("DashcamService", "Started recording: " + file.getAbsolutePath());

            loopTask = this::startNewClip;
            handler.postDelayed(loopTask, CLIP_DURATION_MS);

            cleanupOldFiles(dir);

        } catch (IOException e) {
            Log.e("DashcamService", "Error starting MediaRecorder", e);
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (RuntimeException ignored) {}
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (handler != null && loopTask != null) {
            handler.removeCallbacks(loopTask);
        }
    }

    private void cleanupOldFiles(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".mp4"));
        if (files != null && files.length > MAX_FILES) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - MAX_FILES; i++) {
                boolean deleted = files[i].delete();
                Log.d("DashcamService", "Deleted old file: " + files[i].getName() + " -> " + deleted);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
