package com.app.carnavar.hal.orientation;

import android.content.Context;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import com.app.carnavar.hal.sensors.Gyroscope;
import com.app.carnavar.hal.sensors.RotationVector;
import com.app.carnavar.hal.sensors.SensorTypes;
import com.app.carnavar.hal.sensors.VirtualSensor;
import com.app.carnavar.utils.math.MatrixF4x4;
import com.app.carnavar.utils.math.Quaternion;

import java.util.Locale;

// orientation estimator based on rotation vector (fusion accelerometer and magnetometer filtered data) and gyroscope data fusion
public class FusionDeviceOrientationEstimator extends VirtualSensor implements VirtualSensor.SensorListener {

    public static final String TAG = FusionDeviceOrientationEstimator.class.getSimpleName();

    private RotationVector rotationVector;
    private Gyroscope gyroscope;

    private SensorManager sensorManager;
    private WindowManager windowManager;

    private float[] orientationAngles = new float[3];

    public FusionDeviceOrientationEstimator(Context context) {
        super();
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        rotationVector = new RotationVector(context);
        rotationVector.addSensorValuesCaptureListener(this);
        gyroscope = new Gyroscope(context);
        gyroscope.addSensorValuesCaptureListener(this);
    }

    public FusionDeviceOrientationEstimator(Context context, Handler handler) {
        super(handler);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        rotationVector = new RotationVector(context, handler);
        rotationVector.addSensorValuesCaptureListener(this);
        gyroscope = new Gyroscope(context, handler);
        gyroscope.addSensorValuesCaptureListener(this);
    }

    /**
     * The matrix that holds the current rotation
     */
    private final MatrixF4x4 currentOrientationRotationMatrix = new MatrixF4x4();

    /**
     * The quaternion that holds the current rotation
     */
    private final Quaternion currentOrientationQuaternion = new Quaternion();

    /**
     * Get the current rotation of the device in the rotation matrix format (4x4 matrix)
     */
    public void getRotationMatrix(MatrixF4x4 matrix) {
        matrix.set(currentOrientationRotationMatrix);
    }

    /**
     * Get the current rotation of the device in the quaternion format (vector4f)
     */
    public void getQuaternion(Quaternion quaternion) {
        quaternion.set(currentOrientationQuaternion);
    }

    /**
     * Get the current rotation of the device in the Euler angles
     */
    public void getEulerAngles(float angles[]) {
        SensorManager.getOrientation(currentOrientationRotationMatrix.matrix, angles);
    }

    /**
     * Constant specifying the factor between a Nano-second and a second
     */
    private static final float NS2S = 1.0f / 1000000000.0f;

    /**
     * The quaternion that stores the difference that is obtained by the gyroscope.
     * Basically it contains a rotational difference encoded into a quaternion.
     * <p>
     * To obtain the absolute orientation one must add this into an initial position by
     * multiplying it with another quaternion
     */
    private final Quaternion deltaQuaternion = new Quaternion();

    /**
     * The Quaternions that contain the current rotation (Angle and axis in Quaternion format) of the Gyroscope
     */
    private Quaternion quaternionGyroscope = new Quaternion();

    /**
     * The quaternion that contains the absolute orientation as obtained by the rotationVector sensor.
     */
    private Quaternion quaternionRotationVector = new Quaternion();

    /**
     * The time-stamp being used to record the time when the last gyroscope event occurred.
     */
    private long timestamp;

    /**
     * This is a filter-threshold for discarding Gyroscope measurements that are below a certain level and
     * potentially are only noise and not real motion. Values from the gyroscope are usually between 0 (stop) and
     * 10 (rapid rotation), so 0.1 seems to be a reasonable threshold to filter noise (usually smaller than 0.1) and
     * real motion (usually > 0.1). Note that there is a chance of missing real motion, if the use is turning the
     * device really slowly, so this value has to find a balance between accepting noise (threshold = 0) and missing
     * slow user-action (threshold > 0.5). 0.1 seems to work fine for most applications.
     */
    private static final double EPSILON = 0.05f;

    /**
     * Value giving the total velocity of the gyroscope (will be high, when the device is moving fast and low when
     * the device is standing still). This is usually a value between 0 and 10 for normal motion. Heavy shaking can
     * increase it to about 25. Keep in mind, that these values are time-depended, so changing the sampling rate of
     * the sensor will affect this value!
     */
    private double gyroscopeRotationVelocity = 0;

    /**
     * Flag indicating, whether the orientations were initialised from the rotation vector or not. If false, the
     * gyroscope can not be used (since it's only meaningful to calculate differences from an initial state). If
     * true,
     * the gyroscope can be used normally.
     */
    private boolean positionInitialised = false;

    /**
     * Counter that sums the number of consecutive frames, where the rotationVector and the gyroscope were
     * significantly different (and the dot-product was smaller than 0.7). This event can either happen when the
     * angles of the rotation vector explode (e.g. during fast tilting) or when the device was shaken heavily and
     * the gyroscope is now completely off.
     */
    private int panicCounter;

    /**
     * This weight determines directly how much the rotation sensor will be used to correct (in
     * Sensor-fusion-scenario 1 - SensorSelection.GyroscopeAndRotationVector). Must be a value between 0 and 1.
     * 0 means that the system entirely relies on the gyroscope, whereas 1 means that the system relies entirely on
     * the rotationVector.
     */
    private static final float DIRECT_INTERPOLATION_WEIGHT = 0.1f;

    /**
     * This weight determines indirectly how much the rotation sensor will be used to correct. This weight will be
     * multiplied by the velocity to obtain the actual weight. (in sensor-fusion-scenario 2 -
     * SensorSelection.GyroscopeAndRotationVector2).
     * Must be a value between 0 and approx. 0.04 (because, if multiplied with a velocity of up to 25, should be still
     * less than 1, otherwise the SLERP will not correctly interpolate). Should be close to zero.
     */
    private static final float INDIRECT_INTERPOLATION_WEIGHT = 0.5f;

    /**
     * The threshold that indicates an outlier of the rotation vector. If the dot-product between the two vectors
     * (gyroscope orientation and rotationVector orientation) falls below this threshold (ideally it should be 1,
     * if they are exactly the same) the system falls back to the gyroscope values only and just ignores the
     * rotation vector.
     * <p>
     * This value should be quite high (> 0.7) to filter even the slightest discrepancies that causes jumps when
     * tiling the device. Possible values are between 0 and 1, where a value close to 1 means that even a very small
     * difference between the two sensors will be treated as outlier, whereas a value close to zero means that the
     * almost any discrepancy between the two sensors is tolerated.
     */
    private static final float OUTLIER_THRESHOLD = 0.75f;

    /**
     * The threshold that indicates a massive discrepancy between the rotation vector and the gyroscope orientation.
     * If the dot-product between the two vectors
     * (gyroscope orientation and rotationVector orientation) falls below this threshold (ideally it should be 1, if
     * they are exactly the same), the system will start increasing the panic counter (that probably indicates a
     * gyroscope failure).
     * <p>
     * This value should be lower than OUTLIER_THRESHOLD (0.5 - 0.7) to only start increasing the panic counter,
     * when there is a
     * huge discrepancy between the two fused sensors.
     */
    private static final float OUTLIER_PANIC_THRESHOLD = 0.65f;

    /**
     * The threshold that indicates that a chaos state has been established rather than just a temporary peak in the
     * rotation vector (caused by exploding angled during fast tilting).
     * <p>
     * If the chaosCounter is bigger than this threshold, the current position will be reset to whatever the
     * rotation vector indicates.
     */
    private static final int PANIC_THRESHOLD = 5;

    /**
     * Some temporary variables to save allocations
     */
    final private float[] temporaryQuaternion = new float[4];
    final private Quaternion correctedQuaternion = new Quaternion();
    final private Quaternion interpolatedQuaternion = new Quaternion();

    @Override
    public void onSensorValuesCaptured(float[] values, int sensorType, long timeNanos) {
        if (sensorType == SensorTypes.ORIENTATION_ROTATION_VECTOR) {
            // Process rotation vector (just safe it)
            // Calculate angle. Starting with API_18, Android will provide this value as event.values[3], but if not, we have to calculate it manually.
            SensorManager.getQuaternionFromVector(temporaryQuaternion, values);
            // Store in quaternion
            quaternionRotationVector.setXYZW(temporaryQuaternion[1], temporaryQuaternion[2], temporaryQuaternion[3], -temporaryQuaternion[0]);
            if (!positionInitialised) {
                // Override
                quaternionGyroscope.set(quaternionRotationVector);
                positionInitialised = true;
            }

        } else if (sensorType == SensorTypes.GYROSCOPE_ANGLE_VELOCITY) {
            // Process Gyroscope and perform fusion

            // This timestep's delta rotation to be multiplied by the current rotation
            // after computing it from the gyro sample data.
            if (timeNanos != 0) {
                // freq = (count++) / ((thisTimestamp - initTimestamp) * NS2S);
                // dt = 1.0f / freq; // more stability
                final float dT = (timeNanos - this.timestamp) * NS2S;
                // Axis of the rotation sample, not normalized yet.
                float axisX = values[0];
                float axisY = values[1];
                float axisZ = values[2];

                // Calculate the angular speed of the sample
                gyroscopeRotationVelocity = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);

                // Normalize the rotation vector if it's big enough to get the axis
                if (gyroscopeRotationVelocity > EPSILON) {
                    axisX /= gyroscopeRotationVelocity;
                    axisY /= gyroscopeRotationVelocity;
                    axisZ /= gyroscopeRotationVelocity;
                }

                // Integrate around this axis with the angular speed by the timestep
                // in order to get a delta rotation from this sample over the timestep
                // We will convert this axis-angle representation of the delta rotation
                // into a quaternion before turning it into the rotation matrix.
                double thetaOverTwo = gyroscopeRotationVelocity * dT / 2.0f;
                double sinThetaOverTwo = Math.sin(thetaOverTwo);
                double cosThetaOverTwo = Math.cos(thetaOverTwo);
                deltaQuaternion.setX((float) (sinThetaOverTwo * axisX));
                deltaQuaternion.setY((float) (sinThetaOverTwo * axisY));
                deltaQuaternion.setZ((float) (sinThetaOverTwo * axisZ));
                deltaQuaternion.setW(-(float) cosThetaOverTwo);

                // Move current gyro orientation
                deltaQuaternion.multiplyByQuat(quaternionGyroscope, quaternionGyroscope);

                // Calculate dot-product to calculate whether the two orientation sensors have diverged
                // (if the dot-product is closer to 0 than to 1), because it should be close to 1 if both are the same.
                float dotProd = quaternionGyroscope.dotProduct(quaternionRotationVector);

                // If they have diverged, rely on gyroscope only (this happens on some devices when the rotation vector "jumps").
                if (Math.abs(dotProd) < OUTLIER_THRESHOLD) {
                    // Increase panic counter
//                    if (Math.abs(dotProd) < OUTLIER_PANIC_THRESHOLD) {
//                       panicCounter++;
//                    }

                    panicCounter++;
                    // Directly use Gyro
                    setOrientationQuaternionAndMatrix(quaternionGyroscope);

                } else {
                    // Both are nearly saying the same. Perform normal fusion.

                    // Interpolate with a fixed weight between the two absolute quaternions obtained from gyro and rotation vector sensors
                    // The weight should be quite low, so the rotation vector corrects the gyro only slowly, and the output keeps responsive.
                    quaternionGyroscope.slerp(quaternionRotationVector, interpolatedQuaternion, DIRECT_INTERPOLATION_WEIGHT);
//                    quaternionGyroscope.slerp(quaternionRotationVector, interpolatedQuaternion, (float) (INDIRECT_INTERPOLATION_WEIGHT * gyroscopeRotationVelocity));

                    // Use the interpolated value between gyro and rotationVector
                    setOrientationQuaternionAndMatrix(interpolatedQuaternion);
                    // Override current gyroscope-orientation
                    quaternionGyroscope.copyVec4(interpolatedQuaternion);
                    // Reset the panic counter because both sensors are saying the same again
                    panicCounter = 0;
                }

                if (panicCounter > PANIC_THRESHOLD) {
                    Log.d(TAG, "Panic counter is bigger than threshold; this indicates a Gyroscope failure. Panic reset is imminent.");

                    if (gyroscopeRotationVelocity < 3) {
                        Log.d(TAG, "Performing Panic-reset. Resetting orientation to rotation-vector value.");

                        // Manually set position to whatever rotation vector says.
                        setOrientationQuaternionAndMatrix(quaternionRotationVector);
                        // Override current gyroscope-orientation with corrected value
                        quaternionGyroscope.copyVec4(quaternionRotationVector);
                        panicCounter = 0;
                    } else {
                        Log.d(TAG, String.format(
                                "Panic reset delayed due to ongoing motion (user is still shaking the device). Gyroscope Velocity: %.2f > 3",
                                gyroscopeRotationVelocity));
                    }
                }
            }
            this.timestamp = timeNanos;

            // generate event to update
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

            float[] adjustedRotationMatrix = new float[16];
            SensorManager.remapCoordinateSystem(currentOrientationRotationMatrix.matrix, worldAxisForDeviceAxisX,
                    worldAxisForDeviceAxisY, adjustedRotationMatrix);

            float[] angles = new float[3];
            SensorManager.getOrientation(adjustedRotationMatrix, angles);

            // convert to degrees
            orientationAngles[1] = (float) Math.toDegrees(angles[1]);
            orientationAngles[2] = (float) Math.toDegrees(angles[2]);
            orientationAngles[0] = ((float) Math.toDegrees(angles[0]) + 360f) % 360f; // scale to 0-360 deg

            notifyAllSensorValuesCaptureListeners(orientationAngles, SensorTypes.ORIENTATION_ROTATION_ANGLES, timeNanos);
        }
    }

    /**
     * Sets the output quaternion and matrix with the provided quaternion and synchronises the setting
     *
     * @param quaternion The Quaternion to set (the result of the sensor fusion)
     */
    private void setOrientationQuaternionAndMatrix(Quaternion quaternion) {
        correctedQuaternion.set(quaternion);
        // We inverted w in the deltaQuaternion, because currentOrientationQuaternion required it.
        // Before converting it back to matrix representation, we need to revert this process
        correctedQuaternion.w(-correctedQuaternion.w());
        // Use gyro only
        currentOrientationQuaternion.copyVec4(quaternion);
        // Set the rotation matrix as well to have both representations
        SensorManager.getRotationMatrixFromVector(currentOrientationRotationMatrix.matrix, correctedQuaternion.array());
    }

    public static String toString(float[] values) {
        return String.format(Locale.ENGLISH, TAG + ": azimuth(ovZ)=%1$.1f pitch(ovX)=%2$.1f roll(ovY)=%3$.1f",
                values[0], values[1], values[2]);
    }

    @Override
    public void start() {
        if (gyroscope != null) {
            gyroscope.start();
        }
        if (rotationVector != null) {
            rotationVector.start();
        }
    }

    @Override
    public void stop() {
        if (gyroscope != null) {
            gyroscope.stop();
        }
        if (rotationVector != null) {
            rotationVector.stop();
        }
    }
}