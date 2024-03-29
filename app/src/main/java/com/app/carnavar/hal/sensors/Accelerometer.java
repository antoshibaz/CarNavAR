package com.app.carnavar.hal.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

import java.util.Locale;

public class Accelerometer extends VirtualSensor implements SensorEventListener {

    public static final String TAG = Accelerometer.class.getSimpleName();

    public static final Integer ALL_ACCELERATION = Sensor.TYPE_ACCELEROMETER; // linear acceleration+gravity
    public static final Integer LINEAR_ACCELERATION = Sensor.TYPE_LINEAR_ACCELERATION; // only linear acceleration
    public static final Integer GRAVITY_ACCELERATION = Sensor.TYPE_GRAVITY; // only gravity

    private Sensor sensor;
    private SensorManager sensorManager;

    public Accelerometer(Context context) {
        super();
        init(context);
    }

    public Accelerometer(Context context, Handler handler) {
        super(handler);
        init(context);
    }

    private void init(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rawValues = new float[3];
        sensor = sensorManager.getDefaultSensor(ALL_ACCELERATION);
    }

    // Ax, Ay, Az accelerations in m/s^2
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == ALL_ACCELERATION) {
            System.arraycopy(event.values, 0, rawValues, 0, rawValues.length);
            notifyAllSensorValuesCaptureListeners(getRawValues(), SensorTypes.FULL_ACCELERATION, event.timestamp);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public static String toString(float[] values) {
        return String.format(Locale.ENGLISH, TAG + ": Ax=%1$.1f Ay=%2$.1f Az=%3$.1f m/s^2",
                values[0], values[1], values[2]);
    }

    @Override
    public void start() {
        stop();
        if (handler != null) {
            sensorManager.registerListener(this, sensor, sampleRatePeriodTimeMicros, handler);
        } else {
            sensorManager.registerListener(this, sensor, sampleRatePeriodTimeMicros);
        }
    }

    @Override
    public void stop() {
        sensorManager.unregisterListener(this);
    }
}
