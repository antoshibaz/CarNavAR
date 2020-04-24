package com.app.carnavar.hal.sensors;

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

public abstract class VirtualSensor {

    public static final String TAG = VirtualSensor.class.getSimpleName();

    private List<SensorListener> sensorListeners = new ArrayList<>();

    protected float[] rawValues = null;
    protected int sampleRatePeriodTimeMicros = 100_000; // SensorManager.SENSOR_DELAY_GAME=20000 SENSOR_DELAY_FASTEST=0:

    protected Handler handler = null;

    public interface SensorListener {
        void onSensorValuesCaptured(float[] values, int sensorType, long timeNanos); // timeNanos ref -> SystemClock.elapsedRealtimeNanos()
    }

    public synchronized void addSensorValuesCaptureListener(SensorListener sensorListener) {
        if (sensorListener != null && !sensorListeners.contains(sensorListener)) {
            sensorListeners.add(sensorListener);
        }
    }

    public synchronized void removeSensorValuesCaptureListener(SensorListener sensorListener) {
        if (sensorListener != null && sensorListeners.size() > 0) {
            sensorListeners.remove(sensorListener);
        }
    }

    public synchronized void notifyAllSensorValuesCaptureListeners(float[] values, int sensorType, long timeNanos) {
        for (SensorListener sensorListener : sensorListeners) {
            sensorListener.onSensorValuesCaptured(values, sensorType, timeNanos);
        }
    }

    public VirtualSensor() {
    }

    public VirtualSensor(Handler handler) {
        this.handler = handler;
    }

    public VirtualSensor(Handler handler, int sampleRatePeriodTimeMicros) {
        this.handler = handler;
        this.sampleRatePeriodTimeMicros = sampleRatePeriodTimeMicros;
    }

    public float[] getRawValues() {
        return rawValues;
    }

    public abstract void start();

    public abstract void stop();
}
