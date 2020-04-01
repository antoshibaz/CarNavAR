package com.app.carnavar.hal.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

import java.util.Locale;

// get rotation vector that transform device coordinate system to Earth coordinate system
public class RotationVector extends VirtualSensor implements SensorEventListener {

    public static final String TAG = RotationVector.class.getSimpleName();
    public static final int SENSOR_UID = 3;

    // rotation vector presents rotation components of rotation matrix
    // ANDROID_ROTATION_VECTOR uses data fusion from accelerometer and magnetometer
    // + smoothing data with maybe kalman filter and other tricks
    private static final Integer ANDROID_ROTATION_VECTOR = Sensor.TYPE_ROTATION_VECTOR;

    private SensorManager sensorManager;

    public RotationVector(Context context) {
        super();
        init(context);
    }

    public RotationVector(Context context, Handler handler) {
        super(handler);
        init(context);
    }

    private void init(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorType = ANDROID_ROTATION_VECTOR;
        rawValues = new float[4];
        sensor = sensorManager.getDefaultSensor(sensorType);
    }

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
        return String.format(Locale.ENGLISH, TAG + ": x*sin(θ/2)=%1$.1f y*sin(θ/2)=%2$.1f z*sin(θ/2)=%3$.1f cos(θ/2)=%4$.1f",
                values[0], values[1], values[2], values[3]);
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
