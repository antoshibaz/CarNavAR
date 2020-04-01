package com.app.carnavar.hal.sensors;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;

import java.util.LinkedList;
import java.util.List;

public abstract class VirtualSensor {

    private List<SensorListener> sensorListeners = new LinkedList<>();

    protected Sensor sensor;
    protected Integer sensorType;
    protected float[] rawValues;
    protected int sampleRateTimeMicros = SensorManager.SENSOR_DELAY_GAME;

    protected Handler handler = null;

    public interface SensorListener {
        void onSensorValuesCaptured(float[] values, int sensorType, long timeNanos); // timeNanos ref -> SystemClock.elapsedRealtimeNanos()
    }

    public synchronized void addSensorDataCaptureListener(SensorListener sensorListener) {
        if (sensorListener != null && !sensorListeners.contains(sensorListener)) {
            sensorListeners.add(sensorListener);
        }
    }

    public synchronized void removeSensorDataCaptureListener(SensorListener sensorListener) {
        if (sensorListener != null && sensorListeners.size() > 0) {
            sensorListeners.remove(sensorListener);
        }
    }

    public synchronized void notifyAllSensorDataCaptureListeners(float[] values, int sensorType, long timeNanos) {
        for (SensorListener sensorListener : sensorListeners) {
            sensorListener.onSensorValuesCaptured(values, sensorType, timeNanos);
        }
    }

    public VirtualSensor() {
    }

    public VirtualSensor(Handler handler) {
        this.handler = handler;
    }

    public VirtualSensor(Handler handler, int sampleRateTimeMicros) {
        this.handler = handler;
        this.sampleRateTimeMicros = sampleRateTimeMicros;
    }

    public float[] getRawValues() {
        return rawValues;
    }

    public abstract void start();

    public abstract void stop();
}
