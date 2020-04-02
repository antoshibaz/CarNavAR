package com.app.carnavar.services.gpsimu;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.app.carnavar.hal.sensors.SensorTypes;
import com.app.carnavar.services.ServiceBinder;
import com.app.carnavar.services.gpsimu.GpsImuServiceInterfaces.GpsLocationListener;
import com.app.carnavar.services.gpsimu.GpsImuServiceInterfaces.ImuListener;
import com.app.carnavar.utils.MapsUtils;
import com.app.carnavar.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class GpsImuService extends Service {

    public static final String TAG = GpsImuService.class.getSimpleName();

    private final IBinder binder = new LocalBinder();

    private GpsImuProviderThread gpsImuProviderThread;
    private GpsImuFusionLocationThread gpsImuFusionLocationThread;

    private Handler clientHandler;

    private List<ImuListener> imuListenerList = new ArrayList<>();
    private List<GpsLocationListener> gpsLocationListenerList = new ArrayList<>();

    public void registerImuListener(ImuListener imuListener) {
        if (!imuListenerList.contains(imuListener)) {
            imuListenerList.add(imuListener);
        }
    }

    public void unregisterImuListener(ImuListener imuListener) {
        if (imuListenerList.size() > 0) {
            imuListenerList.remove(imuListener);
        }
    }

    public void notifyAllImuListeners(float[] values, int sensorType, long timeNanos) {
        for (ImuListener imuListener : imuListenerList) {
            imuListener.onImuReturned(values, sensorType, timeNanos);
        }
    }

    public void registerGpsLocationListener(GpsLocationListener gpsLocationListener) {
        if (!gpsLocationListenerList.contains(gpsLocationListener)) {
            gpsLocationListenerList.add(gpsLocationListener);
        }
    }

    public void unregisterGpsLocationListener(GpsLocationListener gpsLocationListener) {
        if (gpsLocationListenerList.size() > 0) {
            gpsLocationListenerList.remove(gpsLocationListener);
        }
    }

    public void notifyAllGpsLocationListeners(Location location) {
        for (GpsLocationListener gpsLocationListener : gpsLocationListenerList) {
            gpsLocationListener.onGpsLocationReturned(location);
        }
    }

    private void publishToClients(Runnable r) {
        if (clientHandler != null) {
            clientHandler.post(r);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        clientHandler = new Handler(Looper.getMainLooper()); // main (ui) thread | or Looper.myLooper()

        gpsImuProviderThread = GpsImuProviderThread.createAndStart(this.getApplicationContext());
        gpsImuProviderThread.setImuListener((values, sensorType, timeNanos) -> {
            switch (sensorType) {
                case SensorTypes.ORIENTATION_ROTATION_ANGLES:
                    // orientation
                    break;
                case SensorTypes.ABSOLUTE_LINEAR_ACCELERATION:
                    // abs accelerations
                    if (gpsImuFusionLocationThread != null) {
                        int north = 0, east = 1, up = 2;
                        if (gpsImuFusionLocationThread.isInitialized()) {
                            gpsImuFusionLocationThread.postPredictTask(values[east], values[north],
                                    TimeUtils.nano2milli(timeNanos));
                        }
                    }
                    break;
            }

            publishToClients(() -> notifyAllImuListeners(values, sensorType, timeNanos));
        });
        gpsImuProviderThread.setGpsLocationListener(location -> {
            // base location
            if (gpsImuFusionLocationThread != null) {
                if (!gpsImuFusionLocationThread.isInitialized()) {
                    gpsImuFusionLocationThread.postInitTask(location);
                } else {
                    gpsImuFusionLocationThread.postUpdateTask(location);
                }
            }
        });

        gpsImuFusionLocationThread = GpsImuFusionLocationThread.createAndStart(this.getApplicationContext());
        gpsImuFusionLocationThread.setGpsLocationListener(location -> {
            // kalman fusion location
            Log.d(TAG, "Kalman fusion location -> " + MapsUtils.toString(location));
            publishToClients(() -> notifyAllGpsLocationListeners(location));
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    public class LocalBinder extends Binder implements ServiceBinder<GpsImuService> {
        @Override
        public GpsImuService getService() {
            return GpsImuService.this;
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
            Log.e(TAG, GpsImuProviderThread.TAG + " throw InterruptedException");
        }

        gpsImuFusionLocationThread.quitSafely();
        try {
            gpsImuFusionLocationThread.join();
            gpsImuFusionLocationThread = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, GpsImuFusionLocationThread.TAG + " throw InterruptedException");
        }
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }
}
