package com.app.carnavar.hal.orientation;

import android.content.Context;
import android.hardware.SensorManager;
import android.os.Handler;
import android.view.Surface;
import android.view.WindowManager;

import com.app.carnavar.hal.sensors.Accelerometer;
import com.app.carnavar.hal.sensors.Gyroscope;
import com.app.carnavar.hal.sensors.Magnetometer;
import com.app.carnavar.hal.sensors.RotationVector;
import com.app.carnavar.hal.sensors.SensorTypes;
import com.app.carnavar.hal.sensors.VirtualSensor;
import com.app.carnavar.utils.TimeUtils;
import com.app.carnavar.utils.math.MatrixF4x4;
import com.app.carnavar.utils.math.Quaternion;


public class FusionImuKinematicEstimator extends VirtualSensor {

    public static final String TAG = FusionImuKinematicEstimator.class.getSimpleName();

    // gyro fusion parameters
    private static final double GYRO_EPSILON = 0.05f;
    private static final float GYRO_OUTLIER_THRESHOLD = 0.75f;
    private static final float GYRO_DIRECT_INTERPOLATION_WEIGHT = 0.5f;

    // sensors
    private Accelerometer accelerometer;
    private Magnetometer magnetometer;
    private Gyroscope gyroscope;
    private RotationVector androidOrientationRotationVector;

    // device accelerations and gravity in device coordinate system
    private float[] gravity = new float[]{0, 0, 0};
    private float[] nonRemappedLinearAccelerations = new float[]{0, 0, 0, 0};
    private float[] linearAccelerations = new float[]{0, 0, 0, 0};
    // device accelerations and gravity in world coordinate system
    private float[] nonRemappedAbsAccelerations = new float[]{0, 0, 0, 0}; // indexes: north=1, east=0, up=2, 3-for matrix op
    private float[] absAccelerations = new float[]{0, 0, 0}; // indexes: north=0, east=1, up=2
    private MatrixF4x4 fusionDeviceOrientationRotationMatrixInv = new MatrixF4x4();

    // for fusion device orientation estimation (gyroscope + rotation vector)
    private float[] tmpOrientationQuaternionValues = new float[4];
    private Quaternion orientationRotationVectorQuaternion = new Quaternion();
    private boolean gyroOrientationInitialised = false;
    private Quaternion gyroOrientationQuaternion = new Quaternion();
    private long gyroLastTimestamp = 0;
    private double gyroRotationVelocity = 0;
    private Quaternion gyroDeltaRotationQuaternion = new Quaternion();
    private Quaternion fusedInterpolOrientationQuaternion = new Quaternion();
    private int gyroPanicCounter = 0;

    // for device orientation in non-remapped coordinate system (sensors system -> world system)
    private Quaternion fusionDeviceOrientationQuaternion = new Quaternion();
    private MatrixF4x4 fusionDeviceOrientationRotationMatrix = new MatrixF4x4();
    private float[] fusionDeviceOrientationAngles = new float[3];
    private boolean orientationInitialized = false;

    // for device orientation in remapped coordinate system (sensors system -> remapped sensor system -> world system)
    private MatrixF4x4 currentDeviceOrientationRotationMatrix = new MatrixF4x4();
    private float[] currentDeviceOrientationAngles = new float[3];

    private SensorManager sensorManager;
    private WindowManager windowManager;

    public FusionImuKinematicEstimator(Context context) {
        super();
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        accelerometer = new Accelerometer(context);
        accelerometer.addSensorValuesCaptureListener(sensorsListener);
        gyroscope = new Gyroscope(context);
        gyroscope.addSensorValuesCaptureListener(sensorsListener);
        androidOrientationRotationVector = new RotationVector(context);
        androidOrientationRotationVector.addSensorValuesCaptureListener(sensorsListener);
    }

    public FusionImuKinematicEstimator(Context context, Handler handler) {
        super(handler);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        accelerometer = new Accelerometer(context, handler);
        accelerometer.addSensorValuesCaptureListener(sensorsListener);
        gyroscope = new Gyroscope(context, handler);
        gyroscope.addSensorValuesCaptureListener(sensorsListener);
        androidOrientationRotationVector = new RotationVector(context, handler);
        androidOrientationRotationVector.addSensorValuesCaptureListener(sensorsListener);
    }

    private SensorListener sensorsListener = new SensorListener() {
        @Override
        public void onSensorValuesCaptured(float[] values, int sensorType, long timeNanos) {
            switch (sensorType) {
                case SensorTypes.FULL_ACCELERATION: // accelerometer values (linear + gravity)
                    if (orientationInitialized) {
                        android.opengl.Matrix.invertM(fusionDeviceOrientationRotationMatrixInv.matrix,
                                0, fusionDeviceOrientationRotationMatrix.matrix, 0);
                        processAccelerometer(values, timeNanos);
                        android.opengl.Matrix.multiplyMV(nonRemappedAbsAccelerations, 0,
                                fusionDeviceOrientationRotationMatrixInv.matrix, 0,
                                nonRemappedLinearAccelerations, 0);

                        int north = 1, east = 0, up = 2;
//                        switch (windowManager.getDefaultDisplay().getRotation()) {
//                            case Surface.ROTATION_90:
//                            case Surface.ROTATION_270:
//                            case Surface.ROTATION_180:
//                            case Surface.ROTATION_0:
//                            default:
//                                break;
//                        }

                        // TODO: check and fix its because sometimes gives NAN
                        absAccelerations[0] = nonRemappedAbsAccelerations[north];
                        absAccelerations[1] = nonRemappedAbsAccelerations[east];
                        absAccelerations[2] = nonRemappedAbsAccelerations[up];
                        notifyAllSensorValuesCaptureListeners(absAccelerations, SensorTypes.ABSOLUTE_LINEAR_ACCELERATION,
                                TimeUtils.currentAndroidSystemTimeNanos());
                    }
                    break;
                case SensorTypes.GYROSCOPE_ANGLE_VELOCITY: // gyroscope values
                    if (timeNanos != 0) {
                        processGyroscopeFusion(values, timeNanos);
                        if (!orientationInitialized) orientationInitialized = true;
                        notifyAllSensorValuesCaptureListeners(currentDeviceOrientationAngles, SensorTypes.ORIENTATION_ROTATION_ANGLES,
                                TimeUtils.currentAndroidSystemTimeNanos());
                    }
                    break;
                case SensorTypes.ORIENTATION_ROTATION_VECTOR: // orientation rotation vector values (accelerometer + magnetometer android native fusion)
                    // get orientation rotation quaternion
                    SensorManager.getQuaternionFromVector(tmpOrientationQuaternionValues, values);
                    orientationRotationVectorQuaternion.setXYZW(tmpOrientationQuaternionValues[1],
                            tmpOrientationQuaternionValues[2],
                            tmpOrientationQuaternionValues[3],
                            -tmpOrientationQuaternionValues[0]);
                    // init gyroscope orientation if required
                    if (!gyroOrientationInitialised) {
                        gyroOrientationQuaternion.set(orientationRotationVectorQuaternion);
                        gyroOrientationInitialised = true;
                    }
                    break;
            }
        }
    };

    private void processAccelerometer(float[] accValues, long timestamp) {
        // approach based on orientation rotation vector
        // azimuth/yaw, pitch, roll
        float azimuth = fusionDeviceOrientationAngles[0],
                pitch = fusionDeviceOrientationAngles[1],
                roll = fusionDeviceOrientationAngles[2];

        // Find the gravity component of the X-axis
        // = g*-cos(pitch)*sin(roll);
        gravity[0] = (float) (-SensorManager.GRAVITY_EARTH * Math.cos(pitch) * Math
                .sin(roll));
        // Find the gravity component of the Y-axis
        // = g*-sin(pitch);
        gravity[1] = (float) (SensorManager.GRAVITY_EARTH * -Math.sin(pitch));
        // Find the gravity component of the Z-axis
        // = g*cos(pitch)*cos(roll);
        gravity[2] = (float) (SensorManager.GRAVITY_EARTH * Math.cos(pitch) * Math
                .cos(roll));

        nonRemappedLinearAccelerations[0] = (accValues[0] - gravity[0]) / SensorManager.GRAVITY_EARTH; //x
        nonRemappedLinearAccelerations[1] = (accValues[1] - gravity[1]) / SensorManager.GRAVITY_EARTH; //y
        nonRemappedLinearAccelerations[2] = (accValues[2] - gravity[2]) / SensorManager.GRAVITY_EARTH; //z

        switch (windowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                linearAccelerations[1] = (accValues[0] - gravity[0]) / SensorManager.GRAVITY_EARTH;
                linearAccelerations[0] = (accValues[1] - gravity[1]) / SensorManager.GRAVITY_EARTH;
                linearAccelerations[2] = (accValues[2] - gravity[2]) / SensorManager.GRAVITY_EARTH;
                break;
            case Surface.ROTATION_180:
            case Surface.ROTATION_0:
            default:
                linearAccelerations[0] = (accValues[0] - gravity[0]) / SensorManager.GRAVITY_EARTH; //x
                linearAccelerations[1] = (accValues[1] - gravity[1]) / SensorManager.GRAVITY_EARTH; //y
                linearAccelerations[2] = (accValues[2] - gravity[2]) / SensorManager.GRAVITY_EARTH; //z
                break;
        }
    }

    private void processGyroscopeFusion(float[] gyroValues, long timestamp) {
        // freq = (count++) / ((thisTimestamp - initTimestamp) * NS2S);
        // dt = 1.0f / freq; // more stability
        final float dT = TimeUtils.nano2sec(timestamp - gyroLastTimestamp);

        // Axis of the rotation sample, not normalized yet.
        float axisX = gyroValues[0];
        float axisY = gyroValues[1];
        float axisZ = gyroValues[2];

        // Calculate the angular speed of the sample
        gyroRotationVelocity = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);

        // Normalize the rotation vector if it's big enough to get the axis
        if (gyroRotationVelocity > GYRO_EPSILON) {
            axisX /= gyroRotationVelocity;
            axisY /= gyroRotationVelocity;
            axisZ /= gyroRotationVelocity;
        }

        // Integrate around this axis with the angular speed by the timestep
        // in order to get a delta rotation from this sample over the timestep
        // We will convert this axis-angle representation of the delta rotation
        // into a quaternion before turning it into the rotation matrix.
        double thetaOverTwo = gyroRotationVelocity * dT / 2.0f;
        double sinThetaOverTwo = Math.sin(thetaOverTwo);
        double cosThetaOverTwo = Math.cos(thetaOverTwo);
        gyroDeltaRotationQuaternion.setX((float) (sinThetaOverTwo * axisX));
        gyroDeltaRotationQuaternion.setY((float) (sinThetaOverTwo * axisY));
        gyroDeltaRotationQuaternion.setZ((float) (sinThetaOverTwo * axisZ));
        gyroDeltaRotationQuaternion.setW(-(float) cosThetaOverTwo);

        // Move current gyro orientation
        gyroDeltaRotationQuaternion.multiplyByQuat(gyroOrientationQuaternion, gyroOrientationQuaternion);

        // Calculate dot-product to calculate whether the two orientation sensors have diverged
        // (if the dot-product is closer to 0 than to 1), because it should be close to 1 if both are the same.
        float dotProd = gyroOrientationQuaternion.dotProduct(orientationRotationVectorQuaternion);

        // If they have diverged, rely on gyroscope only (this happens on some devices when the rotation vector "jumps").
        if (Math.abs(dotProd) < GYRO_OUTLIER_THRESHOLD) {
            gyroPanicCounter++;
            // Directly use Gyro
            updateDeviceOrientation(gyroOrientationQuaternion);
        } else {
            // Both are nearly saying the same. Perform normal fusion.
            // Interpolate with a fixed weight between the two absolute quaternions obtained from gyro and rotation vector sensors
            // The weight should be quite low, so the rotation vector corrects the gyro only slowly, and the output keeps responsive.
            //TODO: maybe use complimentary filter for fastest inference
            gyroOrientationQuaternion.slerp(orientationRotationVectorQuaternion, fusedInterpolOrientationQuaternion, GYRO_DIRECT_INTERPOLATION_WEIGHT);
            //quaternionGyroscope.slerp(quaternionRotationVector, interpolatedQuaternion, (float) (INDIRECT_INTERPOLATION_WEIGHT * gyroscopeRotationVelocity));

            // Use the interpolated value between gyro and rotationVector
            updateDeviceOrientation(fusedInterpolOrientationQuaternion);
            // Override current gyroscope-orientation
            gyroOrientationQuaternion.copyVec4(fusedInterpolOrientationQuaternion);
            // Reset the panic counter because both sensors are saying the same again
            gyroPanicCounter = 0;
        }

        if (gyroPanicCounter > 3) {
            // use android rotation vector
            // Manually set position to whatever rotation vector says
            updateDeviceOrientation(orientationRotationVectorQuaternion);
            // Override current gyroscope-orientation with corrected value
            gyroOrientationQuaternion.copyVec4(orientationRotationVectorQuaternion);
            gyroPanicCounter = 0;
        }

        gyroLastTimestamp = timestamp;
    }

    private void updateDeviceOrientation(Quaternion newOrientationQuaternion) {
        fusionDeviceOrientationQuaternion.set(newOrientationQuaternion);
        // We inverted w in the deltaQuaternion, because currentOrientationQuaternion required it.
        // Before converting it back to matrix representation, we need to revert this process
        fusionDeviceOrientationQuaternion.w(-fusionDeviceOrientationQuaternion.w());
        // Set the rotation matrix as well to have both representations
        SensorManager.getRotationMatrixFromVector(fusionDeviceOrientationRotationMatrix.matrix,
                fusionDeviceOrientationQuaternion.array());
        float[] angles = new float[3];
        SensorManager.getOrientation(fusionDeviceOrientationRotationMatrix.matrix, angles);
        // angles in radians
        fusionDeviceOrientationAngles[1] = angles[1];
        fusionDeviceOrientationAngles[2] = angles[2];
        fusionDeviceOrientationAngles[0] = angles[0];

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

        SensorManager.remapCoordinateSystem(fusionDeviceOrientationRotationMatrix.matrix, worldAxisForDeviceAxisX,
                worldAxisForDeviceAxisY, currentDeviceOrientationRotationMatrix.matrix);
        SensorManager.getOrientation(currentDeviceOrientationRotationMatrix.matrix, angles);
        // convert angles radians to degrees
        currentDeviceOrientationAngles[1] = (float) Math.toDegrees(angles[1]);
        currentDeviceOrientationAngles[2] = (float) Math.toDegrees(angles[2]);
        currentDeviceOrientationAngles[0] = ((float) Math.toDegrees(angles[0]) + 360f) % 360f; // scale to 0-360 deg
    }

    @Override
    public void start() {
        if (accelerometer != null) {
            accelerometer.start();
        }
        if (magnetometer != null) {
            magnetometer.start();
        }
        if (gyroscope != null) {
            gyroscope.start();
        }
        if (androidOrientationRotationVector != null) {
            androidOrientationRotationVector.start();
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
        if (gyroscope != null) {
            gyroscope.stop();
        }
        if (androidOrientationRotationVector != null) {
            androidOrientationRotationVector.stop();
        }
    }
}