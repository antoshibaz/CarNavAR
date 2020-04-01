package com.app.carnavar.hal.location;

import android.location.Location;
import android.os.Handler;

import java.util.LinkedList;
import java.util.List;

public abstract class GpsLocationEngine {

    protected Handler handler = null;

    private List<LocationUpdateListener> locationListenerList = new LinkedList<>();
    protected Location lastLocation;

    public interface LocationUpdateListener {
        void onLocationUpdated(Location location);
    }

    public synchronized void addLocationUpdateListener(LocationUpdateListener locationUpdateListener) {
        if (locationUpdateListener != null && !locationListenerList.contains(locationUpdateListener)) {
            locationListenerList.add(locationUpdateListener);
        }
    }

    public synchronized void removeLocationUpdateListener(LocationUpdateListener locationUpdateListener) {
        if (locationUpdateListener != null && locationListenerList.size() > 0) {
            locationListenerList.remove(locationUpdateListener);
        }
    }

    public synchronized void notifyAllLocationUpdateListeners(Location location) {
        for (LocationUpdateListener locationListener : locationListenerList) {
            locationListener.onLocationUpdated(location);
        }
    }

    public int getListenersCount() {
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
