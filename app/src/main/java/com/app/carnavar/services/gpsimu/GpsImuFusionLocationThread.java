package com.app.carnavar.services.gpsimu;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;

import com.app.carnavar.hal.location.KalmanGpsImuFusionEngine;
import com.app.carnavar.services.gpsimu.GpsImuServiceInterfaces.GpsLocationListener;
import com.app.carnavar.utils.android.TimeUtils;
import com.app.carnavar.utils.maps.MapsUtils;

public class GpsImuFusionLocationThread extends HandlerThread {

    public static final String TAG = GpsImuFusionLocationThread.class.getSimpleName();

    private static boolean useGpsSpeed = false;

    private Context context;
    private Handler handler;

    private KalmanGpsImuFusionEngine kalmanGpsImuFusionEngine;
    private volatile boolean isInitialized = false;

    private volatile Location lastLocation = null;

    private GpsLocationListener gpsLocationListener;

    public void setGpsLocationListener(GpsLocationListener gpsLocationListener) {
        this.gpsLocationListener = gpsLocationListener;
    }

    public void removeGpsLocationListener(GpsLocationListener gpsLocationListener) {
        this.gpsLocationListener = null;
    }

    private GpsImuFusionLocationThread(Context context, String name) {
        super(name);
    }

    private GpsImuFusionLocationThread(Context context, String name, int priority) {
        super(name, priority);
    }

    public KalmanGpsImuFusionEngine retrieveFusionLocationProvider() {
        return kalmanGpsImuFusionEngine;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void postTask(Runnable r) {
        handler.post(r);
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
            long timeMillis = TimeUtils.nanos2millis(location.getElapsedRealtimeNanos());
            kalmanGpsImuFusionEngine.init(x, y, xVel, yVel, posVar, timeMillis);
            isInitialized = true;
        });
    }

    public void postPredictTask(double xAcceleration, double yAcceleration, long timeMillis) { // xAcc-east, yAcc-north
        handler.post(() -> {
//            Log.d(TAG, "predict " + xAcceleration + " " + yAcceleration + " " + timeMillis);
            kalmanGpsImuFusionEngine.predict(xAcceleration, yAcceleration, timeMillis);
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
            long timeMillis = TimeUtils.nanos2millis(location.getElapsedRealtimeNanos());
            //Log.d(TAG, "update " + x + " " + y + " " + speed + " " + " " + bearing + " " + xVel + " " + yVel + " " + velVar + " " + timeMillis);
            kalmanGpsImuFusionEngine.update(x, y, xVel, yVel, posVar, velVar, timeMillis);
            Location newLocation = createLocationAfterUpdate(location);

            // sometimes filter gives is NAN - WTF?
            // TODO: fix problem with filter NAN -> abs acceleration sometimes is NAN
            if (!Double.isNaN(newLocation.getLongitude()) || !Double.isNaN(newLocation.getLatitude()) ||
                    !Double.isNaN(newLocation.getSpeed())) {
                if (newLocation.getLatitude() != 0 && newLocation.getLongitude() != 0) {
                    lastLocation = newLocation;
                    if (gpsLocationListener != null) {
                        gpsLocationListener.onGpsLocationReturned(newLocation);
                    }
                }
            } else {
                Log.e(TAG, "Kalman is NAN. Reset it!");
                postResetTask();
            }
        });
    }

    public void postResetTask() {
        if (lastLocation != null) {
            postInitTask(lastLocation);
        }
    }

    private Location createLocationAfterUpdate(Location rawLocation) {
        Location newLocation = new Location(TAG);
        double[] loc = MapsUtils.getLatLngByDistances(kalmanGpsImuFusionEngine.getCurrentX(),
                kalmanGpsImuFusionEngine.getCurrentY());
        newLocation.setLatitude(loc[0]);
        newLocation.setLongitude(loc[1]);
        newLocation.setAltitude(rawLocation.getAltitude());
        double speed;
        if (kalmanGpsImuFusionEngine.getKalmanOptions().isUseGpsSpeed()) {
            double xVel = kalmanGpsImuFusionEngine.getCurrentXVel();
            double yVel = kalmanGpsImuFusionEngine.getCurrentYVel();
            speed = Math.sqrt(xVel * xVel + yVel * yVel); //scalar speed without bearing
        } else {
            speed = rawLocation.getSpeed();
        }
        newLocation.setSpeed((float) speed);
        newLocation.setBearing(rawLocation.getBearing());
        newLocation.setTime(TimeUtils.currentJavaSystemTimestampMillis());
        newLocation.setElapsedRealtimeNanos(TimeUtils.currentAndroidSystemTimeNanos());
        newLocation.setAccuracy(rawLocation.getAccuracy());
        //Log.d(TAG, "post update " + kalmanGpsImuFusionEngine.getCurrentX() +
        //        " " + kalmanGpsImuFusionEngine.getCurrentY() + " " + loc[0] + " " + loc[1] + " " + xVel + " " + yVel);
        return newLocation;
    }

    public static GpsImuFusionLocationThread createAndStart(Context context) {
        GpsImuFusionLocationThread gpsImuFusionLocationThread = new GpsImuFusionLocationThread(context, TAG, Process.THREAD_PRIORITY_BACKGROUND);
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
            gpsImuFusionLocationThread.kalmanGpsImuFusionEngine = new KalmanGpsImuFusionEngine(KalmanGpsImuFusionEngine.KalmanOptions.Options()
                    .useGpsSpeed(useGpsSpeed) // TODO: with useGpsSpeed=true sometimes gives location incorrectly -> fix it
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

    public Location getLastLocation() {
        return lastLocation;
    }
}
