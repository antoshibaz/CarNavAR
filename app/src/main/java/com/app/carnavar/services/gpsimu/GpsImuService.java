package com.app.carnavar.services.gpsimu;

import android.app.Service;
import android.content.Intent;
import android.hardware.GeomagneticField;
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
import com.app.carnavar.utils.android.TimeUtils;
import com.app.carnavar.utils.maps.MapsUtils;

import java.util.ArrayList;
import java.util.List;

public class GpsImuService extends Service {

    public static final String TAG = GpsImuService.class.getSimpleName();

    private final IBinder binder = new LocalBinder();

    private ImuProviderThread imuProviderThread;
    private GpsProviderThread gpsProviderThread;
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
            Log.d(TAG, gpsLocationListener.toString() + " is registered");
        }
    }

    public void unregisterGpsLocationListener(GpsLocationListener gpsLocationListener) {
        if (gpsLocationListenerList.size() > 0) {
            gpsLocationListenerList.remove(gpsLocationListener);
            Log.d(TAG, gpsLocationListener.toString() + " is unregistered");
        }
    }

    public void notifyAllGpsLocationListeners(Location location) {
        for (GpsLocationListener gpsLocationListener : gpsLocationListenerList) {
            gpsLocationListener.onGpsLocationReturned(location);
        }
    }

    public void requestLastGpsLocation(GpsLocationListener gpsLocationListener) {
        Location lastLoc = gpsImuFusionLocationThread.getLastLocation();
        if (lastLoc != null) {
            gpsLocationListener.onGpsLocationReturned(gpsImuFusionLocationThread.getLastLocation());
        }
    }

    public void requestLastImuValues(ImuListener imuListener) {
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

        imuProviderThread = ImuProviderThread.createAndStart(this.getApplicationContext());
        imuProviderThread.setImuListener((values, sensorType, timeNanos) -> {
            publishToClients(() -> notifyAllImuListeners(values, sensorType, timeNanos));

            if (gpsImuFusionLocationThread != null) {
                Location lastLocation = gpsImuFusionLocationThread.getLastLocation();
                if (lastLocation != null) {
                    imuProviderThread.postTask(() -> {
                        imuProviderThread.retrieveImuProvider().setGeomagneticField(new GeomagneticField(
                                (float) lastLocation.getLatitude(),
                                (float) lastLocation.getLongitude(),
                                (float) lastLocation.getAltitude(),
                                lastLocation.getTime()));
                    });
                }

                switch (sensorType) {
                    case SensorTypes.ORIENTATION_ROTATION_ANGLES: // orientation
                        break;
                    case SensorTypes.ABSOLUTE_LINEAR_ACCELERATION: // abs accelerations
                        // TODO: fix NAN
                        if (Float.isNaN(values[0]) || Float.isNaN(values[1])) {
                            Log.e(TAG, "abs accelerations is NAN. Values is dropped!");
                            return;
                        }
                        int north = 0, east = 1, up = 2;
                        if (gpsImuFusionLocationThread.isInitialized()) {
                            gpsImuFusionLocationThread.postPredictTask(values[east], values[north],
                                    TimeUtils.nanos2millis(timeNanos));
                        }
                        break;
                }
            }
        });

        gpsProviderThread = GpsProviderThread.createAndStart(this.getApplicationContext());
        gpsProviderThread.setGpsLocationListener(location -> {
            // base location
            Log.d(TAG, "Android location -> " + MapsUtils.toString(location));
            //publishToClients(() -> notifyAllGpsLocationListeners(location));
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
        if (imuProviderThread != null) {
            imuProviderThread.quitSafely();
            try {
                imuProviderThread.join();
                imuProviderThread = null;
            } catch (final InterruptedException e) {
                Log.e(TAG, ImuProviderThread.TAG + " throw InterruptedException");
            }
        }

        if (gpsProviderThread != null) {
            gpsProviderThread.quitSafely();
            try {
                gpsProviderThread.join();
                gpsProviderThread = null;
            } catch (final InterruptedException e) {
                Log.e(TAG, GpsProviderThread.TAG + " throw InterruptedException");
            }
        }

        if (gpsImuFusionLocationThread != null) {
            gpsImuFusionLocationThread.quitSafely();
            try {
                gpsImuFusionLocationThread.join();
                gpsImuFusionLocationThread = null;
            } catch (final InterruptedException e) {
                Log.e(TAG, GpsImuFusionLocationThread.TAG + " throw InterruptedException");
            }
        }
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }
}
