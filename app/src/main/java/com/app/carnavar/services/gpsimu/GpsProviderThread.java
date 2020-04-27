package com.app.carnavar.services.gpsimu;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.app.carnavar.hal.location.MapboxGpsLocationEngine;

public class GpsProviderThread extends HandlerThread {

    public static final String TAG = GpsProviderThread.class.getSimpleName();

    private Context context;
    private Handler handler;
    private MapboxGpsLocationEngine mapboxGpsLocationEngine;
    private GpsImuServiceInterfaces.GpsLocationListener gpsLocationListener;

    public void setGpsLocationListener(GpsImuServiceInterfaces.GpsLocationListener gpsLocationListener) {
        this.gpsLocationListener = gpsLocationListener;
    }

    public void removeGpsLocationListener(GpsImuServiceInterfaces.GpsLocationListener gpsLocationListener) {
        this.gpsLocationListener = null;
    }

    public MapboxGpsLocationEngine retrieveGpsProvider() {
        return mapboxGpsLocationEngine;
    }

    private GpsProviderThread(Context context, String name) {
        super(name);
        this.context = context;
    }

    private GpsProviderThread(Context context, String name, int priority) {
        super(name, priority);
        this.context = context;
    }

    public void postTask(Runnable r) {
        handler.post(r);
    }

    private void shutdown() {
        if (mapboxGpsLocationEngine != null) {
            mapboxGpsLocationEngine.stop();
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

    public static GpsProviderThread createAndStart(Context context) {
        GpsProviderThread gpsProviderThread = new GpsProviderThread(context, TAG, Process.THREAD_PRIORITY_BACKGROUND);
        initAndStart(gpsProviderThread);
        return gpsProviderThread;
    }

    public static GpsProviderThread createAndStart(Context context, String name) {
        GpsProviderThread gpsProviderThread = new GpsProviderThread(context, name);
        initAndStart(gpsProviderThread);
        return gpsProviderThread;
    }

    public static GpsProviderThread createAndStart(Context context, String name, int priority) {
        GpsProviderThread gpsProviderThread = new GpsProviderThread(context, name, priority);
        initAndStart(gpsProviderThread);
        return gpsProviderThread;
    }

    private static void initAndStart(GpsProviderThread gpsProviderThread) {
        gpsProviderThread.start();
        gpsProviderThread.handler = new Handler(gpsProviderThread.getLooper());
        gpsProviderThread.handler.post(() -> {
            gpsProviderThread.mapboxGpsLocationEngine = new MapboxGpsLocationEngine(
                    gpsProviderThread.context,
                    gpsProviderThread.handler
            );
            gpsProviderThread.mapboxGpsLocationEngine.addGpsLocationUpdateListener(location -> {
                if (gpsProviderThread.gpsLocationListener != null) {
                    gpsProviderThread.gpsLocationListener.onGpsLocationReturned(location);
                }
            });
            gpsProviderThread.mapboxGpsLocationEngine.start();
        });
    }
}
