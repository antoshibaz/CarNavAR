package com.app.carnavar.hal.location;

import android.content.Context;
import android.os.Handler;

import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;

import androidx.annotation.NonNull;

public class MapboxGpsLocationEngine extends GpsLocationEngine implements LocationEngineCallback<LocationEngineResult> {

    public static final String TAG = MapboxGpsLocationEngine.class.getSimpleName();

    private com.mapbox.android.core.location.LocationEngine mapboxLocationEngine;

    public MapboxGpsLocationEngine(Context context) {
        super();
        init(context);
    }

    public MapboxGpsLocationEngine(Context context, Handler handler) {
        super(handler);
        init(context);
    }

    private void init(Context context) {
        mapboxLocationEngine = LocationEngineProvider.getBestLocationEngine(context);
    }

    public com.mapbox.android.core.location.LocationEngine retrieveMapboxLocationEngine() {
        return mapboxLocationEngine;
    }

    @Override
    public void onSuccess(LocationEngineResult result) {
        if (result != null) {
            lastLocation = result.getLastLocation();
            notifyAllGpsLocationUpdateListeners(lastLocation);
        }
    }

    @Override
    public void onFailure(@NonNull Exception exception) {

    }

    @Override
    public void start() {
        stop();
        LocationEngineRequest locationEngineRequest = new LocationEngineRequest.Builder(1L).setFastestInterval(1L).build();
        if (handler != null) {
            mapboxLocationEngine.requestLocationUpdates(locationEngineRequest, this, handler.getLooper());
        } else {
            mapboxLocationEngine.requestLocationUpdates(locationEngineRequest, this, null);
        }
        mapboxLocationEngine.getLastLocation(this);
    }

    @Override
    public void stop() {
        mapboxLocationEngine.removeLocationUpdates(this);
    }
}
