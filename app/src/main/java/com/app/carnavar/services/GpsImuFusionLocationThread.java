package com.app.carnavar.services;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.app.carnavar.hal.location.KalmanFusionGpsImu;
import com.app.carnavar.utils.MapsUtils;
import com.app.carnavar.utils.TimeUtils;

public class GpsImuFusionLocationThread extends HandlerThread {

    public static final String TAG = GpsImuFusionLocationThread.class.getSimpleName();

    private Context context;
    private Handler handler;

    private KalmanFusionGpsImu kalmanFusionGpsImu;
    private boolean isInitialized = false;
    private double lastMagneticDeclination = 0;

    private GpsImuProviderThread.GpsListener gpsListener;

    public void setGpsListener(GpsImuProviderThread.GpsListener gpsListener) {
        this.gpsListener = gpsListener;
    }

    public void removeGpsListener(GpsImuProviderThread.GpsListener gpsListener) {
        this.gpsListener = null;
    }

    private GpsImuFusionLocationThread(Context context, String name) {
        super(name);
    }

    private GpsImuFusionLocationThread(Context context, String name, int priority) {
        super(name, priority);
    }

    public KalmanFusionGpsImu retrieveFusionLocationProvider() {
        return kalmanFusionGpsImu;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void postInitTask(Location location) {
        handler.post(() -> {
            double x = MapsUtils.longitudeToMeters(location.getLongitude());
            double y = MapsUtils.latitudeToMeters(location.getLatitude());
            double speed = location.getSpeed();
            double bearing = location.getBearing();
            double xVel = speed * Math.cos(bearing);
            double yVel = speed * Math.sin(bearing);
            double posVar = location.getAccuracy();
            long timeMillis = TimeUtils.nano2milli(location.getElapsedRealtimeNanos());
            kalmanFusionGpsImu.init(x, y, xVel, yVel, posVar, timeMillis);
            isInitialized = true;
        });
    }

    public void postPredictTask(double xAcceleration, double yAcceleration, long timeMillis) {
        handler.post(() -> {
            if (lastMagneticDeclination != 0) {
                // correcting accelerations by magnetic declination
                double yCorrAcc = xAcceleration * Math.cos(lastMagneticDeclination) + yAcceleration * Math.sin(lastMagneticDeclination);
                double xCorrAcc = yAcceleration * Math.cos(lastMagneticDeclination) - xAcceleration * Math.sin(lastMagneticDeclination);
                kalmanFusionGpsImu.predict(xCorrAcc, yCorrAcc, timeMillis);
            } else {
                kalmanFusionGpsImu.predict(xAcceleration, yAcceleration, timeMillis);
            }
        });
    }

    public void postUpdateTask(Location location) {
        handler.post(() -> {
            double x = MapsUtils.longitudeToMeters(location.getLongitude());
            double y = MapsUtils.latitudeToMeters(location.getLatitude());
            double speed = location.getSpeed();
            double bearing = location.getBearing();
            double xVel = speed * Math.cos(bearing);
            double yVel = speed * Math.sin(bearing);
            double posVar = location.getAccuracy();
            double velVar;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
                velVar = location.getSpeedAccuracyMetersPerSecond();
            } else {
                velVar = posVar * 0.1f;
            }
            long timeMillis = TimeUtils.nano2milli(location.getElapsedRealtimeNanos());
            GeomagneticField f = new GeomagneticField(
                    (float) location.getLatitude(),
                    (float) location.getLongitude(),
                    (float) location.getAltitude(),
                    timeMillis);
            lastMagneticDeclination = f.getDeclination();
            kalmanFusionGpsImu.update(x, y, xVel, yVel, posVar, velVar, timeMillis);
            Location newLocation = createLocationAfterUpdate(location);
            if (gpsListener != null) {
                gpsListener.onGpsReturned(newLocation);
            }
        });
    }

    public void postResetTask() {

    }

    private Location createLocationAfterUpdate(Location rawLocation) {
        Location newLocation = new Location(TAG);
        double[] loc = MapsUtils.getLatLngByDistances(kalmanFusionGpsImu.getCurrentX(),
                kalmanFusionGpsImu.getCurrentY());
        newLocation.setLatitude(loc[0]);
        newLocation.setLongitude(loc[1]);
        newLocation.setAltitude(rawLocation.getAltitude());
        double xVel = kalmanFusionGpsImu.getCurrentXVel();
        double yVel = kalmanFusionGpsImu.getCurrentYVel();
        double speed = Math.sqrt(xVel * xVel + yVel * yVel); //scalar speed without bearing
        newLocation.setSpeed((float) speed);
        newLocation.setBearing(rawLocation.getBearing());
        newLocation.setTime(TimeUtils.currentJavaSystemTimestampMillis());
        newLocation.setElapsedRealtimeNanos(TimeUtils.currentAndroidSystemTimeNanos());
        newLocation.setAccuracy(rawLocation.getAccuracy());
        return newLocation;
    }

    private void notifyAllAboutNewLocation() {

    }

    public static GpsImuFusionLocationThread createAndStart(Context context) {
        GpsImuFusionLocationThread gpsImuFusionLocationThread = new GpsImuFusionLocationThread(context, TAG, Process.THREAD_PRIORITY_DEFAULT);
        initAndStart(gpsImuFusionLocationThread);
        return gpsImuFusionLocationThread;
    }

    public static GpsImuFusionLocationThread createAndStart(Context context, String name) {
        GpsImuFusionLocationThread gpsImuFusionLocationThread = new GpsImuFusionLocationThread(context, name);
        initAndStart(gpsImuFusionLocationThread);
        return gpsImuFusionLocationThread;
    }

    public static GpsImuFusionLocationThread createAndStart(Context context, String name, int priority) {
        GpsImuFusionLocationThread gpsImuFusionLocationThread = new GpsImuFusionLocationThread(context, name, priority);
        initAndStart(gpsImuFusionLocationThread);
        return gpsImuFusionLocationThread;
    }

    private static void initAndStart(GpsImuFusionLocationThread gpsImuFusionLocationThread) {
        gpsImuFusionLocationThread.start();
        gpsImuFusionLocationThread.handler = new Handler(gpsImuFusionLocationThread.getLooper());
        gpsImuFusionLocationThread.handler.post(() -> {
            gpsImuFusionLocationThread.kalmanFusionGpsImu = new KalmanFusionGpsImu(KalmanFusionGpsImu.KalmanOptions.Options()
                    .useGpsSpeed(true)
                    .setAccelerationDeviance(0.1f)
                    .setPositionVarianceMulFactor(1.0f)
                    .setVelocityVarianceMulFactor(1.0f)
            );
        });
    }

    private void shutdown() {
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
}
