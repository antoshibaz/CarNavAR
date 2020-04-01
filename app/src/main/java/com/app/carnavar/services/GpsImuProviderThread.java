package com.app.carnavar.services;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.app.carnavar.hal.location.AndroidGpsLocationEngine;
import com.app.carnavar.hal.orientation.FusionImuKinematicEstimator;

public class GpsImuProviderThread extends HandlerThread {

    public static final String TAG = GpsImuProviderThread.class.getSimpleName();

    private Context context;
    private Handler handler;
    private FusionImuKinematicEstimator fusionImuKinematicEstimator;
    private AndroidGpsLocationEngine androidGpsLocationEngine;

    private ImuListener imuListener;
    private GpsListener gpsListener;

    public interface ImuListener {
        void onImuReturned(float[] data, int type, long timeNanos);
    }

    public void setImuListener(ImuListener imuListener) {
        this.imuListener = imuListener;
    }

    public void removeImuListener(ImuListener imuListener) {
        this.imuListener = null;
    }

    public interface GpsListener {
        void onGpsReturned(Location location);
    }

    public void setGpsListener(GpsListener gpsListener) {
        this.gpsListener = gpsListener;
    }

    public void removeImuListener(GpsListener gpsListener) {
        this.gpsListener = null;
    }

    public AndroidGpsLocationEngine retrieveGpsProvider() {
        return androidGpsLocationEngine;
    }

    public FusionImuKinematicEstimator retrieveImuProvider() {
        return fusionImuKinematicEstimator;
    }

    private GpsImuProviderThread(Context context, String name) {
        super(name);
        this.context = context;
    }

    private GpsImuProviderThread(Context context, String name, int priority) {
        super(name, priority);
        this.context = context;
    }

    private void shutdown() {
        if (fusionImuKinematicEstimator != null) {
            fusionImuKinematicEstimator.stop();
            this.imuListener = null;

        }
        if (androidGpsLocationEngine != null) {
            androidGpsLocationEngine.stop();
            this.gpsListener = null;
        }
    }

    @Override
    public boolean quit() {
        shutdown();
        return super.quit();
    }

    @Override
    public boolean quitSafely() {
        shutdown();
        return super.quitSafely();
    }

    public static GpsImuProviderThread createAndStart(Context context) {
        GpsImuProviderThread gpsImuProviderThread = new GpsImuProviderThread(context, TAG, Process.THREAD_PRIORITY_DEFAULT);
        initAndStart(gpsImuProviderThread);
        return gpsImuProviderThread;
    }

    public static GpsImuProviderThread createAndStart(Context context, String name) {
        GpsImuProviderThread gpsImuProviderThread = new GpsImuProviderThread(context, name);
        initAndStart(gpsImuProviderThread);
        return gpsImuProviderThread;
    }

    public static GpsImuProviderThread createAndStart(Context context, String name, int priority) {
        GpsImuProviderThread gpsImuProviderThread = new GpsImuProviderThread(context, name, priority);
        initAndStart(gpsImuProviderThread);
        return gpsImuProviderThread;
    }

    private static void initAndStart(GpsImuProviderThread gpsImuProviderThread) {
        gpsImuProviderThread.start();
        gpsImuProviderThread.handler = new Handler(gpsImuProviderThread.getLooper());
        gpsImuProviderThread.handler.post(() -> {
            gpsImuProviderThread.fusionImuKinematicEstimator = new FusionImuKinematicEstimator(
                    gpsImuProviderThread.context,
                    gpsImuProviderThread.handler
            );
            gpsImuProviderThread.fusionImuKinematicEstimator.addSensorDataCaptureListener((values, sensorType, timeNanos) -> {
                if (gpsImuProviderThread.imuListener != null) {
                    gpsImuProviderThread.imuListener.onImuReturned(values, sensorType, timeNanos);
                }
            });

            gpsImuProviderThread.androidGpsLocationEngine = new AndroidGpsLocationEngine(
                    gpsImuProviderThread.context,
                    gpsImuProviderThread.handler
            );
            gpsImuProviderThread.androidGpsLocationEngine.addLocationUpdateListener(location -> {
                if (gpsImuProviderThread.gpsListener != null) {
                    gpsImuProviderThread.gpsListener.onGpsReturned(location);
                }
            });

            gpsImuProviderThread.fusionImuKinematicEstimator.start();
            gpsImuProviderThread.androidGpsLocationEngine.start();
        });
    }
}
