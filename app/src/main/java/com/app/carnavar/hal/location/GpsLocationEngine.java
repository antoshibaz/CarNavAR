package com.app.carnavar.hal.location;

import android.location.Location;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

public abstract class GpsLocationEngine {

    public static final String TAG = GpsLocationEngine.class.getSimpleName();

    protected Handler handler = null;

    private List<GpsLocationUpdateListener> locationListenerList = new ArrayList<>();
    protected Location lastLocation;

    public interface GpsLocationUpdateListener {
        void onGpsLocationUpdated(Location location);
    }

    public synchronized void addGpsLocationUpdateListener(GpsLocationUpdateListener gpsLocationUpdateListener) {
        if (gpsLocationUpdateListener != null && !locationListenerList.contains(gpsLocationUpdateListener)) {
            locationListenerList.add(gpsLocationUpdateListener);
        }
    }

    public synchronized void removeGpsLocationUpdateListener(GpsLocationUpdateListener gpsLocationUpdateListener) {
        if (gpsLocationUpdateListener != null && locationListenerList.size() > 0) {
            locationListenerList.remove(gpsLocationUpdateListener);
        }
    }

    public synchronized void notifyAllGpsLocationUpdateListeners(Location location) {
        for (GpsLocationUpdateListener locationListener : locationListenerList) {
            locationListener.onGpsLocationUpdated(location);
        }
    }

    public int getGpsLocationListenersCount() {
        return locationListenerList.size();
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public GpsLocationEngine() {
    }

    public GpsLocationEngine(Handler handler) {
        this.handler = handler;
    }

    public abstract void start();

    public abstract void stop();
}
