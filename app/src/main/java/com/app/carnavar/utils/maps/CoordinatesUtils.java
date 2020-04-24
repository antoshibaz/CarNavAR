package com.app.carnavar.utils.maps;

import android.location.Location;
import android.opengl.Matrix;
import android.util.Size;

import com.google.ar.sceneform.math.Vector3;

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
    public static float[] convertECEFtoENU(Location currentLocation, float[] ecefCurrentLocation, float[] ecefPOI) {
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

    // Convert GPS coordinate (Latitude, Longitude, Altitude) to Navigation coordinate (East step, North step, Up step)
    public static float[] convertWSG84toENU(Location gpsSrcLocation, Location gpsDstLocation) {
        float[] srcInECEF = convertWSG84toECEF(gpsSrcLocation);
        float[] dstInECEF = convertWSG84toECEF(gpsDstLocation);
        float[] dstInENU = convertECEFtoENU(gpsSrcLocation, srcInECEF, dstInECEF);
        return dstInENU;
    }

    // gpsRefLocation - center of coordinates (camera location), gpsDstLocation - desirable point for conversation
    public static float[] convertGpsToWorld(Location gpsRefLocation, Location gpsDstLocation) {
        return convertWSG84toENU(gpsRefLocation, gpsDstLocation);
    }

    public static float[] convertWorldToScreen(float[] worldLocation,
                                               float[] cameraProjectionMatrix,
                                               float[] cameraOrientationRotationMatrix,
                                               Size cameraScreenFovSize) {
        float[] cameraScreenCoordinateVector = new float[4];
        float[] rotatedToOrientationCameraProjectionMatrix = new float[16];
        Matrix.multiplyMM(rotatedToOrientationCameraProjectionMatrix, 0, cameraProjectionMatrix,
                0, cameraOrientationRotationMatrix, 0);
        Matrix.multiplyMV(cameraScreenCoordinateVector, 0, rotatedToOrientationCameraProjectionMatrix,
                0, worldLocation, 0);

        // cameraCoordinateVector[2] is z, that always less than 0 to display on right position
        // if z > 0, the point will display on the opposite
        if (cameraScreenCoordinateVector[2] < 0) {
            float x = (0.5f + cameraScreenCoordinateVector[0] / cameraScreenCoordinateVector[3]) * cameraScreenFovSize.getWidth();
            float y = (0.5f - cameraScreenCoordinateVector[1] / cameraScreenCoordinateVector[3]) * cameraScreenFovSize.getHeight();
            return new float[]{x, y};
        }

        return null;
    }

    public static float[] convertWorldToScreen(float[] worldLocation,
                                               float[] rotatedToOrientationCameraProjectionMatrix,
                                               Size cameraScreenFovSize) {
        float[] cameraScreenCoordinateVector = new float[4];
        Matrix.multiplyMV(cameraScreenCoordinateVector, 0, rotatedToOrientationCameraProjectionMatrix,
                0, worldLocation, 0);

        // cameraCoordinateVector[2] is z, that always less than 0 to display on right position
        // if z > 0, the point will display on the opposite
        if (cameraScreenCoordinateVector[2] < 0) {
//            float x = (0.5f + cameraScreenCoordinateVector[0] / cameraScreenCoordinateVector[3]) * cameraScreenFovSize.getWidth();
//            float y = (0.5f - cameraScreenCoordinateVector[1] / cameraScreenCoordinateVector[3]) * cameraScreenFovSize.getHeight();
//            return new float[]{x, y};
            cameraScreenCoordinateVector[0] = cameraScreenCoordinateVector[0] / cameraScreenCoordinateVector[3];
            cameraScreenCoordinateVector[1] = cameraScreenCoordinateVector[1] / cameraScreenCoordinateVector[3];

            float[] screenCoords = new float[]{0f, 0f};
            screenCoords[0] = (float) (cameraScreenFovSize.getWidth() * ((cameraScreenCoordinateVector[0] + 1.0) / 2.0));
            screenCoords[1] = (float) (cameraScreenFovSize.getHeight() * ((1.0 - cameraScreenCoordinateVector[1]) / 2.0));

            return screenCoords;
        }

        return null;
    }

    // convert GPS coordinate (Latitude, Longitude, Altitude) to camera screen coordinate (x, y)
    public static float[] convertGpsToScreen(Location srcPoint, Location dstPoint,
                                             float[] cameraProjectionMatrix,
                                             float[] cameraOrientationRotationMatrix,
                                             Size cameraScreenFovSize) {
        float[] dstInENU = convertGpsToWorld(srcPoint, dstPoint);
        return convertWorldToScreen(dstInENU, cameraProjectionMatrix, cameraOrientationRotationMatrix, cameraScreenFovSize);
    }

    // convert GPS coordinate (Latitude, Longitude, Altitude) to camera screen coordinate (x, y)
    public static float[] convertGpsToScreen(Location srcPoint, Location dstPoint,
                                             float[] rotatedToOrientationCameraProjectionMatrix,
                                             Size cameraScreenFovSize) {
        float[] dstInENU = convertGpsToWorld(srcPoint, dstPoint);
        return convertWorldToScreen(dstInENU, rotatedToOrientationCameraProjectionMatrix, cameraScreenFovSize);
    }

    public static float[] getCameraProjectionMatrix(Size cameraScreenFovSize) {
        float ratio = 0, l = -1f, r = 1f, b = -1f, t = 1f, zNear = 0.5f, zFar = 10000f;
        if (cameraScreenFovSize.getWidth() < cameraScreenFovSize.getHeight()) {
            ratio = (float) cameraScreenFovSize.getWidth() / cameraScreenFovSize.getHeight();
            l *= ratio;
            r *= ratio;
        } else {
            ratio = (float) cameraScreenFovSize.getHeight() / cameraScreenFovSize.getWidth();
            b *= ratio;
            t *= ratio;
        }

        float[] projectionMatrix = new float[16];
        Matrix.frustumM(projectionMatrix, 0, l, r, b, t, zNear, zFar);
        return projectionMatrix;
    }

    public static float[] getWorld2CameraScreenMatrix(float[] worldModelMtx, float[] camScreenViewMtx, float[] camScreenProjMtx) {
        float scaleFactor = 1.0f;
        float[] scaleMatrix = new float[16];
        float[] modelXscale = new float[16];
        float[] viewXmodelXscale = new float[16];
        float[] world2screenMatrix = new float[16];

        Matrix.setIdentityM(scaleMatrix, 0);
        scaleMatrix[0] = scaleFactor;
        scaleMatrix[5] = scaleFactor;
        scaleMatrix[10] = scaleFactor;

        Matrix.multiplyMM(modelXscale, 0, worldModelMtx, 0, scaleMatrix, 0);
        Matrix.multiplyMM(viewXmodelXscale, 0, camScreenViewMtx, 0, modelXscale, 0);
        Matrix.multiplyMM(world2screenMatrix, 0, camScreenProjMtx, 0, viewXmodelXscale, 0);

        return world2screenMatrix;
    }

    public static float[] convertWorld2Screen(int screenWidth, int screenHeight, float[] world2cameraMatrix) {
        float[] origin = {0f, 0f, 0f, 1f};
        float[] ndcCoords = new float[4];
        Matrix.multiplyMV(ndcCoords, 0, world2cameraMatrix, 0, origin, 0);

        ndcCoords[0] = ndcCoords[0] / ndcCoords[3];
        ndcCoords[1] = ndcCoords[1] / ndcCoords[3];

        float[] screenCoords = new float[]{0f, 0f};
        screenCoords[0] = (float) (screenWidth * ((ndcCoords[0] + 1.0) / 2.0));
        screenCoords[1] = (float) (screenHeight * ((1.0 - ndcCoords[1]) / 2.0));

        return screenCoords;
    }

    public static Vector3 worldToScreenPoint(Vector3 point,
                                             com.google.ar.sceneform.math.Matrix projMat,
                                             com.google.ar.sceneform.math.Matrix viewMat,
                                             Size screenSize) {
        com.google.ar.sceneform.math.Matrix m = new com.google.ar.sceneform.math.Matrix();
        com.google.ar.sceneform.math.Matrix.multiply(projMat, viewMat, m);

        int viewWidth = screenSize.getWidth();
        int viewHeight = screenSize.getHeight();
        float x = point.x;
        float y = point.y;
        float z = point.z;
        float w = 1.0f;

        // Multiply the world point.
        Vector3 screenPoint = new Vector3();
        screenPoint.x = x * m.data[0] + y * m.data[4] + z * m.data[8] + w * m.data[12];
        screenPoint.y = x * m.data[1] + y * m.data[5] + z * m.data[9] + w * m.data[13];
        screenPoint.z = x * m.data[2] + y * m.data[6] + z * m.data[10] + w * m.data[14];
        w = x * m.data[3] + y * m.data[7] + z * m.data[11] + w * m.data[15];

        // To clipping space.
        screenPoint.x = ((screenPoint.x / w) + 1.0f) * 0.5f;
        screenPoint.y = ((screenPoint.y / w) + 1.0f) * 0.5f;

        // To screen space.
        screenPoint.x = screenPoint.x * viewWidth;
        screenPoint.y = screenPoint.y * viewHeight;

        // Invert Y because screen Y points down and Sceneform Y points up.
        screenPoint.y = viewHeight - screenPoint.y;

        return screenPoint;
    }

    public static float getMinSignedAnglesDiff(float a1, float a2) {
        float absDiff = 180 - Math.abs(Math.abs(a1 - a2) - 180);
        return (((a1 + absDiff) + 360) % 360 == a2) ? absDiff : -absDiff;
    }
}
