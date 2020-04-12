package com.app.carnavar.utils;

import android.location.Location;
import android.opengl.Matrix;
import android.util.Size;

public class CoordinatesUtils {

    private final static double WGS84_A = 6378137.0; // WGS84 semi-major axis constant in meters
    private final static double WGS84_E2 = 0.00669437999014; // square of WGS 84 eccentricity

    // https://github.com/dat-ng/ar-location-based-android
    // Convert GPS coordinate (Latitude, Longitude, Altitude) to ECEF coordinate (Earth-centered Earth-fixed coordinate)
    public static float[] convertWSG84toECEF(Location location) {
        double radLat = Math.toRadians(location.getLatitude());
        double radLon = Math.toRadians(location.getLongitude());

        float clat = (float) Math.cos(radLat);
        float slat = (float) Math.sin(radLat);
        float clon = (float) Math.cos(radLon);
        float slon = (float) Math.sin(radLon);

        float N = (float) (WGS84_A / Math.sqrt(1.0 - WGS84_E2 * slat * slat));
        float x = (float) ((N + location.getAltitude()) * clat * clon);
        float y = (float) ((N + location.getAltitude()) * clat * slon);
        float z = (float) ((N * (1.0 - WGS84_E2) + location.getAltitude()) * slat);

        return new float[]{x, y, z};
    }

    // Convert ECEF coordinate (Earth-centered Earth-fixed coordinate) to Navigation coordinate (East step, North step, Up step)
    public static float[] convertSystemECEFtoENU(Location currentLocation, float[] ecefCurrentLocation, float[] ecefPOI) {
        double radLat = Math.toRadians(currentLocation.getLatitude());
        double radLon = Math.toRadians(currentLocation.getLongitude());

        float clat = (float) Math.cos(radLat);
        float slat = (float) Math.sin(radLat);
        float clon = (float) Math.cos(radLon);
        float slon = (float) Math.sin(radLon);

        float dx = ecefCurrentLocation[0] - ecefPOI[0];
        float dy = ecefCurrentLocation[1] - ecefPOI[1];
        float dz = ecefCurrentLocation[2] - ecefPOI[2];

        float east = -slon * dx + clon * dy;
        float north = -slat * clon * dx - slat * slon * dy + clat * dz;
        float up = clat * clon * dx + clat * slon * dy + slat * dz;

        return new float[]{east, north, up, 1};
    }

    public static void convertGpsToWorld() {

    }

    public static void convertWorldToCameraScreen() {

    }

    // convert GPS coordinate (Latitude, Longitude, Altitude) to camera screen coordinate (x, y)
    public static float[] convertGpsToCameraScreen(Location srcPoint, Location dstPoint,
                                                   float[] cameraProjectionMatrix,
                                                   float[] cameraOrientationRotationMatrix,
                                                   Size cameraScreenFovSize) {
        float[] srcInECEF = convertWSG84toECEF(srcPoint);
        float[] dstInECEF = convertWSG84toECEF(dstPoint);
        float[] dstInENU = convertSystemECEFtoENU(srcPoint, srcInECEF, dstInECEF);

        float[] cameraScreenCoordinateVector = new float[4];
        float[] rotatedToOrientationCameraProjectionMatrix = new float[16];
        Matrix.multiplyMM(rotatedToOrientationCameraProjectionMatrix, 0, cameraProjectionMatrix,
                0, cameraOrientationRotationMatrix, 0);
        Matrix.multiplyMV(cameraScreenCoordinateVector, 0, rotatedToOrientationCameraProjectionMatrix,
                0, dstInENU, 0);

        // cameraCoordinateVector[2] is z, that always less than 0 to display on right position
        // if z > 0, the point will display on the opposite
        if (cameraScreenCoordinateVector[2] < 0) {
            float x = (0.5f + cameraScreenCoordinateVector[0] / cameraScreenCoordinateVector[3]) * cameraScreenFovSize.getHeight();
            float y = (0.5f - cameraScreenCoordinateVector[1] / cameraScreenCoordinateVector[3]) * cameraScreenFovSize.getHeight();
            return new float[] {x, y};
        }

        return null;
    }

    // Z_NEAR = 0.5f; Z_FAR = 10000;
    public float[] getCameraProjectionMatrix(Size cameraScreenFovSize, float zNear, float zFar) {
        float ratio = 0;

        if (cameraScreenFovSize.getWidth() < cameraScreenFovSize.getHeight()) {
            ratio = (float) cameraScreenFovSize.getWidth() / cameraScreenFovSize.getHeight();
        } else {
            ratio = (float) cameraScreenFovSize.getHeight() / cameraScreenFovSize.getWidth();
        }

        float[] projectionMatrix = new float[16];
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, zNear, zFar);

        return projectionMatrix;
    }
}
