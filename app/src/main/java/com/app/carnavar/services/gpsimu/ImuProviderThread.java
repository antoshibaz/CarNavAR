package com.app.carnavar.services.gpsimu;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.app.carnavar.hal.motion.FusionImuMotionEngine;
import com.app.carnavar.services.gpsimu.GpsImuServiceInterfaces.ImuListener;

public class ImuProviderThread extends HandlerThread {

    public static final String TAG = ImuProviderThread.class.getSimpleName();

    private Context context;
    private Handler handler;
    private FusionImuMotionEngine fusionImuMotionEngine;
    private ImuListener imuListener;

    public void setImuListener(GpsImuServiceInterfaces.ImuListener imuListener) {
        this.imuListener = imuListener;
    }

    public void removeImuListener(ImuListener imuListener) {
        this.imuListener = null;
    }

    public FusionImuMotionEngine retrieveImuProvider() {
        return fusionImuMotionEngine;
    }

    private ImuProviderThread(Context context, String name) {
        super(name);
        this.context = context;
    }

    private ImuProviderThread(Context context, String name, int priority) {
        super(name, priority);
        this.context = context;
    }

    public void postTask(Runnable r) {
        handler.post(r);
    }

    private void shutdown() {
        if (fusionImuMotionEngine != null) {
            fusionImuMotionEngine.stop();
            this.imuListener = null;
        }
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

    public static ImuProviderThread createAndStart(Context context) {
        ImuProviderThread imuProviderThread = new ImuProviderThread(context, TAG, Process.THREAD_PRIORITY_BACKGROUND);
        initAndStart(imuProviderThread);
        return imuProviderThread;
    }

    public static ImuProviderThread createAndStart(Context context, String name) {
        ImuProviderThread imuProviderThread = new ImuProviderThread(context, name);
        initAndStart(imuProviderThread);
        return imuProviderThread;
    }

    public static ImuProviderThread createAndStart(Context context, String name, int priority) {
        ImuProviderThread imuProviderThread = new ImuProviderThread(context, name, priority);
        initAndStart(imuProviderThread);
        return imuProviderThread;
    }

    private static void initAndStart(ImuProviderThread imuProviderThread) {
        imuProviderThread.start();
        imuProviderThread.handler = new Handler(imuProviderThread.getLooper());
        imuProviderThread.handler.post(() -> {
            imuProviderThread.fusionImuMotionEngine = new FusionImuMotionEngine(
                    imuProviderThread.context,
                    imuProviderThread.handler
            );
            imuProviderThread.fusionImuMotionEngine.addSensorValuesCaptureListener((values, sensorType, timeNanos) -> {
                if (imuProviderThread.imuListener != null) {
                    imuProviderThread.imuListener.onImuReturned(values, sensorType, timeNanos);
                }
            });
            imuProviderThread.fusionImuMotionEngine.start();
        });
    }
}
