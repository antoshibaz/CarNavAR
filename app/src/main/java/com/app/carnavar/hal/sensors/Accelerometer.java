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
    public static final int SENSOR_UID = 0;

    public static final Integer ALL_ACCELERATION = Sensor.TYPE_ACCELEROMETER; // linear acceleration+gravity
    public static final Integer LINEAR_ACCELERATION = Sensor.TYPE_LINEAR_ACCELERATION; // only linear acceleration
    public static final Integer GRAVITY_ACCELERATION = Sensor.TYPE_GRAVITY; // only gravity

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
        sensorType = ALL_ACCELERATION;
        rawValues = new float[3];
        sensor = sensorManager.getDefaultSensor(sensorType);
    }

    // Ax, Ay, Az accelerations in m/s^2
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == sensorType) {
            System.arraycopy(event.values, 0, rawValues, 0, rawValues.length);
            notifyAllSensorDataCaptureListeners(getRawValues(), SENSOR_UID, event.timestamp);
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
            sensorManager.registerListener(this, sensor, sampleRateTimeMicros, handler);
        } else {
            sensorManager.registerListener(this, sensor, sampleRateTimeMicros);
        }
    }

    @Override
    public void stop() {
        sensorManager.unregisterListener(this);
    }
}
