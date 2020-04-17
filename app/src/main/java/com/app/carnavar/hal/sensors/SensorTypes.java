package com.app.carnavar.hal.sensors;

public abstract class SensorTypes {
    public static final int FULL_ACCELERATION = 0; // Ax, Ay, Az + g
    public static final int LINEAR_ACCELERATION = 1; // Ax, Ay, Az
    public static final int GRAVITY_ACCELERATION = 2; // g
    public static final int ABSOLUTE_LINEAR_ACCELERATION = 3; // A_north, A_east, A_up
    public static final int GYROSCOPE_ANGLE_VELOCITY = 4; // Wx, Wy, Wz
    public static final int MAGNETIC_FIELD = 5; // Mx, My, Mz
    public static final int ORIENTATION_ROTATION_VECTOR = 6; // rotation quaternion
    public static final int ORIENTATION_ROTATION_ANGLES = 7; // rotation euler angles: azimuth/yaw, pitch, roll
    public static final int ORIENTATION_ROTATION_QUATERNION = 8;
}
