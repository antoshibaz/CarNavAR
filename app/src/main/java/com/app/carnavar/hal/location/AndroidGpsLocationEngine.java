package com.app.carnavar.hal.location;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;

public class AndroidGpsLocationEngine extends GpsLocationEngine implements LocationListener {

    public static final String TAG = AndroidGpsLocationEngine.class.getSimpleName();

    private LocationManager locationManager;

    public AndroidGpsLocationEngine(Context context) {
        super();
        init(context);
    }

    public AndroidGpsLocationEngine(Context context, Handler handler) {
        super(handler);
        init(context);
    }

    private void init(Context context) {
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            lastLocation = location;
            notifyAllLocationUpdateListeners(lastLocation);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private static Criteria getCriteria() {
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_HIGH);
        criteria.setAltitudeRequired(true);
        criteria.setSpeedRequired(true);
        criteria.setCostAllowed(true);
        criteria.setBearingRequired(true);
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setBearingAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setSpeedAccuracy(Criteria.ACCURACY_HIGH);
        return criteria;
    }

    public void start() throws SecurityException {
        stop();
        Criteria criteria = getCriteria();
        if (handler != null) {
            locationManager.requestLocationUpdates(0, 0, criteria, this, handler.getLooper());
        } else {
            locationManager.requestLocationUpdates(0, 0, criteria, this, null);
        }
        onLocationChanged(locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false)));
    }

    public void stop() {
        locationManager.removeUpdates(this);
    }
}