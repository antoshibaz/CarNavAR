package com.app.carnavar.hal.orientation;

import android.content.Context;
import android.hardware.SensorManager;
import android.os.Handler;
import android.view.Surface;
import android.view.WindowManager;

import com.app.carnavar.hal.sensors.Accelerometer;
import com.app.carnavar.hal.sensors.Magnetometer;
import com.app.carnavar.hal.sensors.RotationVector;
import com.app.carnavar.hal.sensors.SensorTypes;
import com.app.carnavar.hal.sensors.VirtualSensor;

import java.util.Locale;

// orientation estimator based on rotation vector (fusion accelerometer and magnetometer filtered data)
public class DeviceOrientationEstimator extends VirtualSensor implements VirtualSensor.SensorListener {

    public static final String TAG = DeviceOrientationEstimator.class.getSimpleName();

    private Accelerometer accelerometer;
    private Magnetometer magnetometer;
    private RotationVector rotationVector;

    private SensorManager sensorManager;
    private WindowManager windowManager;

    private float[] orientationAngles = new float[3];
    private boolean useRotVec;

    public DeviceOrientationEstimator(Context context, boolean useRotVec) {
        super();
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.useRotVec = useRotVec;

        if (useRotVec) {
            rotationVector = new RotationVector(context);
            rotationVector.addSensorValuesCaptureListener(this);
        } else {
            accelerometer = new Accelerometer(context);
            magnetometer = new Magnetometer(context);
            accelerometer.addSensorValuesCaptureListener(this);
            magnetometer.addSensorValuesCaptureListener(this);
        }
    }

    public DeviceOrientationEstimator(Context context, boolean useRotVec, Handler handler) {
        super(handler);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.useRotVec = useRotVec;

        if (useRotVec) {
            rotationVector = new RotationVector(context, handler);
            rotationVector.addSensorValuesCaptureListener(this);
        } else {
            accelerometer = new Accelerometer(context, handler);
            magnetometer = new Magnetometer(context, handler);
            accelerometer.addSensorValuesCaptureListener(this);
            magnetometer.addSensorValuesCaptureListener(this);
        }
    }

    public float[] getOrientationAngles() {
        return orientationAngles;
    }

    // Compute the rotation matrix: merges and translates the data
    // from the accelerometer and magnetometer, in the device coordinate
    // system, into a matrix in Earth's coordinate system.
    @Override
    public void onSensorValuesCaptured(float[] values, int sensorType, long timeNanos) {
        float[] rotationMatrix = new float[9];
        if (useRotVec) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector.getRawValues()); // get from rotation vector
        } else {
            SensorManager.getRotationMatrix(rotationMatrix,
                    null, accelerometer.getRawValues(), magnetometer.getRawValues());
        }

        int worldAxisForDeviceAxisX;
        int worldAxisForDeviceAxisY;

        // Remap the matrix based on current device/activity rotation
        // for vertical portrait device (device ax Y = Earth ax Z) as default
        switch (windowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                break;
            case Surface.ROTATION_180:
                worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                break;
            case Surface.ROTATION_270:
                worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                break;
            case Surface.ROTATION_0:
            default:
                worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                break;
        }

        float[] adjustedRotationMatrix = new float[9];
        SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                worldAxisForDeviceAxisY, adjustedRotationMatrix);

        // for flat portrait device (device ax Z = Earth ax Z) as default
//        float[] rotationMatrixAdjusted = new float[9];
//        switch (mDisplay.getRotation()) {
//            case Surface.ROTATION_0:
//                rotationMatrixAdjusted = rotationMatrix.clone();
//                break;
//            case Surface.ROTATION_90:
//                SensorManager.remapCoordinateSystem(rotationMatrix,
//                        SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X,
//                        rotationMatrixAdjusted);
//                break;
//            case Surface.ROTATION_180:
//                SensorManager.remapCoordinateSystem(rotationMatrix,
//                        SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y,
//                        rotationMatrixAdjusted);
//                break;
//            case Surface.ROTATION_270:
//                SensorManager.remapCoordinateSystem(rotationMatrix,
//                        SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X,
//                        rotationMatrixAdjusted);
//                break;
//        }

        // Express the updated rotation matrix as three orientation angles.
        // azimuth, pitch, roll - in rad
        float[] angles = new float[3];
        SensorManager.getOrientation(adjustedRotationMatrix, angles);

        // convert to degrees
        orientationAngles[1] = (float) Math.toDegrees(angles[1]);
        orientationAngles[2] = (float) Math.toDegrees(angles[2]);
        orientationAngles[0] = ((float) Math.toDegrees(angles[0]) + 360f) % 360f; // scale to 0-360 deg

        notifyAllSensorValuesCaptureListeners(getOrientationAngles(), SensorTypes.ORIENTATION_ROTATION_ANGLES, timeNanos);
    }

    public static String toString(float[] values) {
        return String.format(Locale.ENGLISH, TAG + ": azimuth(ovZ)=%1$.1f pitch(ovX)=%2$.1f roll(ovY)=%3$.1f",
                values[0], values[1], values[2]);
    }

    @Override
    public void start() {
        if (accelerometer != null) {
            accelerometer.start();
        }
        if (magnetometer != null) {
            magnetometer.start();
        }
        if (rotationVector != null) {
            rotationVector.start();
        }
    }

    @Override
    public void stop() {
        if (accelerometer != null) {
            accelerometer.stop();
        }
        if (magnetometer != null) {
            magnetometer.stop();
        }
        if (rotationVector != null) {
            rotationVector.stop();
        }
    }
}
