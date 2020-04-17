package com.app.carnavar.hal.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.view.WindowManager;

import com.app.carnavar.hal.orientation.FusionDeviceAttitudeEstimator;
import com.app.carnavar.utils.android.TimeUtils;

import java.util.Locale;

public class LinearAccelerometer extends VirtualSensor implements SensorEventListener, VirtualSensor.SensorListener {

    public static final String TAG = LinearAccelerometer.class.getSimpleName();

    public static final Integer ALL_ACCELERATION = Sensor.TYPE_ACCELEROMETER; // linear acceleration+gravity

    private static final float NS2S = 1.0f / 1000000000.0f;
    private long initTimestamp = System.nanoTime(), thisTimestamp = System.nanoTime();
    private long count = 0;
    private float freq = 0;
    private float dt = 0;
    private float alpha = 0.1f;
    private float timeConstant = 0.018f;
    private float[] gravity = new float[]{0, 0, 0};
    private float[] linAcc = new float[]{0, 0, 0};
    private float[] eps = new float[]{0.002f, 0.002f, 0.002f}; // empirical

    private FusionDeviceAttitudeEstimator deviceOrientationEstimator;
    private float[] orientationAngles = new float[]{0, 0, 0};
    private float[] gravity2 = new float[]{0, 0, 0};
    private float[] linAcc2 = new float[]{0, 0, 0};

    private Sensor sensor;
    private SensorManager sensorManager;
    private WindowManager windowManager;

    public LinearAccelerometer(Context context) {
        super();
        init(context);
    }

    public LinearAccelerometer(Context context, Handler handler) {
        super(handler);
        init(context);
    }

    private void init(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        rawValues = new float[3];
        sensor = sensorManager.getDefaultSensor(ALL_ACCELERATION);
        deviceOrientationEstimator = new FusionDeviceAttitudeEstimator(context, handler);
        deviceOrientationEstimator.addSensorValuesCaptureListener(this);
    }

    // Ax, Ay, Az accelerations in m/s^2
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == ALL_ACCELERATION) {
            System.arraycopy(event.values, 0, rawValues, 0, rawValues.length);

            // approach 1 based on low-pass filtering
//            thisTimestamp = System.nanoTime();
//            freq = (count++) / ((thisTimestamp - initTimestamp) * NS2S);
//            dt = 1.0f / freq;
//
//            // timeConstant=0.18 and dt=1/50 => alpha=0.9 | timeConstant=0.002 and dt=1/50 => alpha=0.1
//            alpha = timeConstant / (timeConstant + dt); // for this code
//            // timeConstant=0.18 and dt=1/50 => alpha=0.1 | timeConstant=0.002 and dt=1/50 => alpha=0.9
//            // alpha = dt / (timeConstant + dt) // for classical exp moving average
//
//            gravity[0] = alpha * gravity[0] + (1 - alpha) * rawValues[0];
//            gravity[1] = alpha * gravity[1] + (1 - alpha) * rawValues[1];
//            gravity[2] = alpha * gravity[2] + (1 - alpha) * rawValues[2];
//            linAcc[0] = rawValues[0] - gravity[0];
//            linAcc[1] = rawValues[1] - gravity[1];
//            linAcc[2] = rawValues[2] - gravity[2];
//
//            linAcc[0] = (linAcc[0] > 0) ? linAcc[0] - eps[0] : linAcc[0] + eps[0];
//            linAcc[1] = (linAcc[1] > 0) ? linAcc[1] - eps[1] : linAcc[1] + eps[1];
//            linAcc[2] = (linAcc[2] > 0) ? linAcc[2] - eps[2] : linAcc[2] + eps[2];
//
//            if (sensorDataCaptureListener != null) {
//                sensorDataCaptureListener.onDataCaptured(linAcc, SENSOR_UID, thisTimestamp);
//            }

            // approach 2 based on orientation
            if (orientationAngles != null) {
                // azimuth/yaw, pitch, roll
                float azimuth = orientationAngles[0],
                        pitch = (float) Math.toRadians(orientationAngles[1]),
                        roll = (float) Math.toRadians(orientationAngles[2]);
                // Find the gravity component of the X-axis
                // = g*-cos(pitch)*sin(roll);
                gravity2[0] = (float) (-SensorManager.GRAVITY_EARTH * Math.cos(pitch) * Math
                        .sin(roll));
                // Find the gravity component of the Y-axis
                // = g*-sin(pitch);
                gravity2[1] = (float) (SensorManager.GRAVITY_EARTH * -Math.sin(pitch));
                // Find the gravity component of the Z-axis
                // = g*cos(pitch)*cos(roll);
                gravity2[2] = (float) (SensorManager.GRAVITY_EARTH * Math.cos(pitch) * Math
                        .cos(roll));

                linAcc2[0] = (rawValues[0] - gravity2[0]) / SensorManager.GRAVITY_EARTH;
                linAcc2[1] = (rawValues[1] - gravity2[1]) / SensorManager.GRAVITY_EARTH;
                linAcc2[2] = (rawValues[2] - gravity2[2]) / SensorManager.GRAVITY_EARTH;

                notifyAllSensorValuesCaptureListeners(linAcc2, SensorTypes.LINEAR_ACCELERATION, TimeUtils.currentAndroidSystemTimeNanos());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorValuesCaptured(float[] values, int sensorType, long timeNanos) {
        switch (sensorType) {
            case SensorTypes.ORIENTATION_ROTATION_ANGLES: {
                System.arraycopy(values, 0, orientationAngles, 0, values.length);
            }
        }
    }

    public static String toString(float[] values) {
        return String.format(Locale.ENGLISH, TAG + ": Ax=%1$.3f Ay=%2$.3f Az=%3$.3f m/s^2",
                values[0], values[1], values[2]);
    }

    @Override
    public void start() {
        stop();
        deviceOrientationEstimator.stop();
        if (handler != null) {
            sensorManager.registerListener(this, sensor, sampleRatePeriodTimeMicros, handler);
        } else {
            sensorManager.registerListener(this, sensor, sampleRatePeriodTimeMicros);
        }
        deviceOrientationEstimator.start();
    }

    @Override
    public void stop() {
        sensorManager.unregisterListener(this);
        deviceOrientationEstimator.stop();
    }
}
