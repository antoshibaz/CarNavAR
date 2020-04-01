package com.app.carnavar.services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.app.carnavar.hal.orientation.FusionImuKinematicEstimator;
import com.app.carnavar.utils.MapsUtils;
import com.app.carnavar.utils.TimeUtils;

public class GpsImuService extends Service {

    public static final String TAG = GpsImuService.class.getSimpleName();

    private static GpsImuService gpsImuService = null;
    private final IBinder binder = new LocalBinder();

    private GpsImuProviderThread gpsImuProviderThread;
    private GpsImuFusionLocationThread gpsImuFusionLocationThread;

    private Handler clientHandler;

    public static GpsImuService getInstance() {
        if (gpsImuService == null) {
            gpsImuService = new GpsImuService();
        }
        return gpsImuService;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        clientHandler = new Handler(Looper.myLooper());

        gpsImuProviderThread = GpsImuProviderThread.createAndStart(this.getApplicationContext());
        gpsImuProviderThread.setImuListener((data, type, timeNanos) -> {
            switch (type) {
                case FusionImuKinematicEstimator.TYPE_ORIENTATION_ANGLES:
                    // orientation
//                        Log.d(TAG + " orientation:", Arrays.toString(data));
                    break;
                case FusionImuKinematicEstimator.TYPE_ABS_LINEAR_ACCELERATIONS:
                    // abs accelerations
//                        Log.d(TAG, String.format(" abs accelerations: %1f %2f %3f", data[0], data[1], data[2]));
                    if (gpsImuFusionLocationThread != null) {
                        int north = 0, east = 1, up = 2;
                        if (gpsImuFusionLocationThread.isInitialized()) {
                            gpsImuFusionLocationThread.postPredictTask(data[east], data[north],
                                    TimeUtils.nano2milli(timeNanos));
                        }
                    }
                    break;
            }
        });
        gpsImuProviderThread.setGpsListener(location -> {
            // base location
//            Log.d(TAG + " location:", MapsUtils.toString(location));
//            Timber.tag(TAG).d("location: %1s", MapsUtils.toString(location));
            if (gpsImuFusionLocationThread != null) {
                if (!gpsImuFusionLocationThread.isInitialized()) {
                    gpsImuFusionLocationThread.postInitTask(location);
                } else {
                    gpsImuFusionLocationThread.postUpdateTask(location);
                }
            }
        });

        gpsImuFusionLocationThread = GpsImuFusionLocationThread.createAndStart(this.getApplicationContext());
        gpsImuFusionLocationThread.setGpsListener(location -> {
            // fusion location
            Log.d(TAG + " kalman fusion location:", MapsUtils.toString(location));
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    public class LocalBinder extends Binder implements ServiceBinder<GpsImuService> {
        @Override
        public GpsImuService getService() {
            gpsImuService = GpsImuService.this;
            return getInstance();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void shutdown() {
        gpsImuProviderThread.quitSafely();
        try {
            gpsImuProviderThread.join();
            gpsImuProviderThread = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "GpsImuProviderThread InterruptedException");
        }

        gpsImuFusionLocationThread.quitSafely();
        try {
            gpsImuFusionLocationThread.join();
            gpsImuFusionLocationThread = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "GpsImuFusionLocationThread InterruptedException");
        }
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }
}
