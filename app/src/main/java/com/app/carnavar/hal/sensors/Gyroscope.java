package com.app.carnavar.hal.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

import java.util.Locale;

public class Gyroscope extends VirtualSensor implements SensorEventListener {

    public static final String TAG = Gyroscope.class.getSimpleName();
    public static final int SENSOR_UID = 2;

    private static final Integer GYRO = Sensor.TYPE_GYROSCOPE;

    private SensorManager sensorManager;

    public Gyroscope(Context context) {
        super();
        init(context);
    }

    public Gyroscope(Context context, Handler handler) {
        super(handler);
        init(context);
    }

    private void init(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorType = GYRO;
        rawValues = new float[3];
        sensor = sensorManager.getDefaultSensor(sensorType);
    }

    // Gx, Gy, Gz angle velocity in rad/s
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
        return String.format(Locale.ENGLISH, TAG + ":Gx=%1$.1f Gy=%2$.1f Gz=%3$.1f rad/s",
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
