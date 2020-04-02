package com.app.carnavar.services.gpsimu;

import android.location.Location;

public abstract class GpsImuServiceInterfaces {

    public interface ImuListener {
        void onImuReturned(float[] values, int sensorType, long timeNanos);
    }

    public interface GpsLocationListener {
        void onGpsLocationReturned(Location location);
    }
}
