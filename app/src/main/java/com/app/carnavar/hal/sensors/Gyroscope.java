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

    private static final Integer GYRO = Sensor.TYPE_GYROSCOPE;

    private Sensor sensor;
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
        rawValues = new float[3];
        sensor = sensorManager.getDefaultSensor(GYRO);
    }

    // Gx, Gy, Gz angle velocity in rad/s
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == GYRO) {
            System.arraycopy(event.values, 0, rawValues, 0, rawValues.length);
            notifyAllSensorValuesCaptureListeners(getRawValues(), SensorTypes.GYROSCOPE_ANGLE_VELOCITY, event.timestamp);
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
