package com.app.carnavar.utils.android;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public class LibsUtils {

    public static class ARCore {

        private static final String CAMERA_INTRINSICS_TEXT_FORMAT =
                "ARCore Camera %s Intrinsics:\n\tFocal Length: (%.2f, %.2f)"
                        + "\n\tPrincipal Point: (%.2f, %.2f)"
                        + "\n\tImage Dimensions: (%d, %d)"
                        + "\n\tUnrotated FOV: (%.2f˚, %.2f˚)";

        public static Session createARCoreSession(Context context, boolean sessWithSharedCamera) throws UnavailableException {
            Session session = null;
            if (sessWithSharedCamera) {
                session = new Session(context, EnumSet.of(Session.Feature.SHARED_CAMERA));
            } else {
                session = new Session(context);
            }
            Config config = new Config(session);

            // IMPORTANT!!! ArSceneView needs to use the non-blocking update mode.
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE)
                    .setFocusMode(Config.FocusMode.AUTO);
            session.configure(config);

            CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(session).setTargetFps(
                    EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30, CameraConfig.TargetFps.TARGET_FPS_60));
            List<CameraConfig> cameraConfigs = getSessionCameraConfigs(session, cameraConfigFilter);
            CameraConfig cameraConfig = cameraConfigs.get(0);
            // TODO: make function for more flexible filtration
            for (int index = 1; index < cameraConfigs.size(); index++) {
                // filter by highest resolution and camera facing
                if (cameraConfigs.get(index).getImageSize().getHeight()
                        > cameraConfig.getImageSize().getHeight()
                        && cameraConfig.getFacingDirection() == CameraConfig.FacingDirection.BACK) {
                    cameraConfig = cameraConfigs.get(index);
                }
            }

            session.setCameraConfig(cameraConfig);
            return session;
        }

        public static List<CameraConfig> getSessionCameraConfigs(Session session,
                                                                 CameraConfigFilter cameraConfigFilter) {
            return session.getSupportedCameraConfigs(cameraConfigFilter);
        }

        public static String getCameraIntrinsics(Frame frame, boolean intrinsicTypeFlag) {
            Camera camera = frame.getCamera();
            CameraIntrinsics intrinsics =
                    intrinsicTypeFlag ? camera.getTextureIntrinsics() : camera.getImageIntrinsics();
            String intrinsicsLabel = intrinsicTypeFlag ? "Texture" : "Image";

            float[] focalLength = intrinsics.getFocalLength();
            float[] principalPoint = intrinsics.getPrincipalPoint();
            int[] imageSize = intrinsics.getImageDimensions();

            float fovX = (float) (2 * Math.atan2((double) imageSize[0], (double) (2 * focalLength[0])));
            float fovY = (float) (2 * Math.atan2((double) imageSize[1], (double) (2 * focalLength[1])));
            fovX = (float) Math.toDegrees(fovX);
            fovY = (float) Math.toDegrees(fovY);

            return String.format(Locale.ENGLISH, CAMERA_INTRINSICS_TEXT_FORMAT,
                    intrinsicsLabel,
                    focalLength[0],
                    focalLength[1],
                    principalPoint[0],
                    principalPoint[1],
                    imageSize[0],
                    imageSize[1],
                    fovX,
                    fovY);
        }

        // call this method in onCreate() of lifecycle
        public static void checkPeriodicAvailability(Activity activity,
                                                     Runnable runIfAvailable,
                                                     Runnable runIfUnavailable,
                                                     long requestTimePeriodMillis) {
            // Make sure ARCore is supported on this device
            ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(activity);
            if (availability.isTransient()) {
                // use re-query checkAvailability
                new Handler(activity.getMainLooper()).postDelayed(() -> {
                    checkPeriodicAvailability(activity, runIfAvailable, runIfUnavailable, requestTimePeriodMillis);
                }, requestTimePeriodMillis);
            }
            if (availability.isSupported()) {
                runIfAvailable.run();
            }
            if (availability.isUnsupported()) {
                runIfUnavailable.run();
            }
            // availability.isUnknown()==True if availability.isTransient()==True
        }

        // call this method in onCreate() of lifecycle
        public static boolean checkAvailabilityAndInstallIfNeeded(Activity activity) throws UnavailableException {
            // Make sure ARCore is installed and supported on this device
            ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(activity);
            switch (availability) {
                case SUPPORTED_INSTALLED:
                    // arcore supported and arcore apk installed
                    break;
                case SUPPORTED_APK_TOO_OLD:
                case SUPPORTED_NOT_INSTALLED:
                    boolean installed = requestInstalling(activity);
                    if (!installed) {
                        installed = requestInstalling(activity);
                    }
                    return installed;
                case UNKNOWN_CHECKING:
                    return false; // if availability.isTransient()==True
                case UNKNOWN_ERROR:
                case UNKNOWN_TIMED_OUT:
                    throw new UnavailableException();
                case UNSUPPORTED_DEVICE_NOT_CAPABLE:
                    throw new UnavailableDeviceNotCompatibleException();
            }
            return true;
        }

        // call this method in onResume() of lifecycle
        public static boolean requestInstalling(Activity activity) throws UnavailableException {
            // Request ARCore installation
            ArCoreApk.InstallStatus installStatus =
                    ArCoreApk.getInstance().requestInstall(activity,
                            /*userRequestedInstall=*/ true);
            switch (installStatus) {
                case INSTALL_REQUESTED:
                    // arcore apk installation requested
                    // Ensures next invocation of requestInstall() will either return
                    // INSTALLED or throw an exception
                    break;
                case INSTALLED:
                    // arcore apk installed
                    return true;
            }

            return false;
        }

        public static String handleException(UnavailableException exception) {
            String message;
            if (exception instanceof UnavailableArcoreNotInstalledException) {
                message = "UnavailableArcoreNotInstalledException: Please install ARCore";
            } else if (exception instanceof UnavailableApkTooOldException) {
                message = "UnavailableApkTooOldException: Please update ARCore";
            } else if (exception instanceof UnavailableSdkTooOldException) {
                message = "UnavailableSdkTooOldException: Please update this app";
            } else if (exception instanceof UnavailableDeviceNotCompatibleException) {
                message = "UnavailableDeviceNotCompatibleException: This device does not support ARCore";
            } else {
                message = "UnavailableException: Failed to check and install ARCore";
            }
            return message;
        }

        public static String handleCameraConfig(CameraConfig cameraConfig) {
            return String.format(Locale.ENGLISH, "\n\tCamera id: %s Fps: %s Image size: %s Texture size: %s",
                    cameraConfig.getCameraId(), cameraConfig.getFpsRange(), cameraConfig.getImageSize(), cameraConfig.getTextureSize());
        }
    }

    public static class OpenCV {

    }
}
