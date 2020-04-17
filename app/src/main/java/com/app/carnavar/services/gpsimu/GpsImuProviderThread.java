package com.app.carnavar.services.gpsimu;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.app.carnavar.hal.location.AndroidGpsLocationEngine;
import com.app.carnavar.hal.motion.FusionImuMotionEstimator;
import com.app.carnavar.services.gpsimu.GpsImuServiceInterfaces.GpsLocationListener;
import com.app.carnavar.services.gpsimu.GpsImuServiceInterfaces.ImuListener;

public class GpsImuProviderThread extends HandlerThread {

    public static final String TAG = GpsImuProviderThread.class.getSimpleName();

    private Context context;
    private Handler handler;
    private FusionImuMotionEstimator fusionImuMotionEstimator;
    private AndroidGpsLocationEngine androidGpsLocationEngine;

    private ImuListener imuListener;
    private GpsLocationListener gpsLocationListener;

    public void setImuListener(GpsImuServiceInterfaces.ImuListener imuListener) {
        this.imuListener = imuListener;
    }

    public void removeImuListener(ImuListener imuListener) {
        this.imuListener = null;
    }

    public void setGpsLocationListener(GpsLocationListener gpsLocationListener) {
        this.gpsLocationListener = gpsLocationListener;
    }

    public void removeGpsLocationListener(GpsLocationListener gpsLocationListener) {
        this.gpsLocationListener = null;
    }

    public AndroidGpsLocationEngine retrieveGpsProvider() {
        return androidGpsLocationEngine;
    }

    public FusionImuMotionEstimator retrieveImuProvider() {
        return fusionImuMotionEstimator;
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
        if (fusionImuMotionEstimator != null) {
            fusionImuMotionEstimator.stop();
            this.imuListener = null;

        }
        if (androidGpsLocationEngine != null) {
            androidGpsLocationEngine.stop();
            this.gpsLocationListener = null;
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
            gpsImuProviderThread.fusionImuMotionEstimator = new FusionImuMotionEstimator(
                    gpsImuProviderThread.context,
                    gpsImuProviderThread.handler
            );
            gpsImuProviderThread.fusionImuMotionEstimator.addSensorValuesCaptureListener((values, sensorType, timeNanos) -> {
                if (gpsImuProviderThread.imuListener != null) {
                    gpsImuProviderThread.imuListener.onImuReturned(values, sensorType, timeNanos);
                }
            });

            gpsImuProviderThread.androidGpsLocationEngine = new AndroidGpsLocationEngine(
                    gpsImuProviderThread.context,
                    gpsImuProviderThread.handler
            );
            gpsImuProviderThread.androidGpsLocationEngine.addGpsLocationUpdateListener(location -> {
                gpsImuProviderThread.fusionImuMotionEstimator.setGeomagneticField(new GeomagneticField(
                        (float) location.getLatitude(),
                        (float) location.getLongitude(),
                        (float) location.getAltitude(),
                        location.getTime()));

                if (gpsImuProviderThread.gpsLocationListener != null) {
                    gpsImuProviderThread.gpsLocationListener.onGpsLocationReturned(location);
                }
            });

            gpsImuProviderThread.fusionImuMotionEstimator.start();
            gpsImuProviderThread.androidGpsLocationEngine.start();
        });
    }
}
