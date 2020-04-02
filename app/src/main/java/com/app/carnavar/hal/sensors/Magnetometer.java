package com.app.carnavar.hal.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

import java.util.Locale;

public class Magnetometer extends VirtualSensor implements SensorEventListener {

    public static final String TAG = Magnetometer.class.getSimpleName();

    private static final Integer MAGNETIC_FIELD = Sensor.TYPE_MAGNETIC_FIELD;

    private Sensor sensor;
    private SensorManager sensorManager;

    public Magnetometer(Context context) {
        super();
        init(context);
    }

    public Magnetometer(Context context, Handler handler) {
        super(handler);
        init(context);
    }

    private void init(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rawValues = new float[3];
        sensor = sensorManager.getDefaultSensor(MAGNETIC_FIELD);
    }

    // Mx, My, Mz magnetic field in uT
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, rawValues, 0, rawValues.length);
            notifyAllSensorValuesCaptureListeners(getRawValues(), SensorTypes.MAGNETIC_FIELD, event.timestamp);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public static String toString(float[] values) {
        return String.format(Locale.ENGLISH, TAG + ": Mx=%1$.1f My=%2$.1f Mz=%3$.1f uT",
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
