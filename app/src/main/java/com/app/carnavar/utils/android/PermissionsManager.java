package com.app.carnavar.utils.android;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionsManager {

    private static final int PERMISSIONS_REQUEST_CODE = 0;

    private PermissionsListener permissionsListener;

    public interface PermissionsListener {

        void onExplanationNeeded(List<String> permissionsToExplain);

        void onPermissionResult(boolean granted);
    }

    public PermissionsManager(PermissionsListener permissionsListener) {
        this.permissionsListener = permissionsListener;
    }

    // check single permission
    private static boolean permissionIsGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    // absolute check permissions: if one of all is denied then return false granted for all
    public static boolean checkPermissions(Context context, String[] permissions) {
        boolean isGranted = false;
        for (String permission : permissions) {
            isGranted = permissionIsGranted(context, permission);
            if (!isGranted) break;
        }

        return isGranted;
    }

    public void requestPermissions(Activity activity, String[] permissions) {
        ArrayList<String> permissionsToExplain = new ArrayList<>();
        for (String permission : permissions) {
            // if user not check 'Never ask again'
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                permissionsToExplain.add(permission);
            } else {
                // explain and re-request permission
            }
        }

        if (permissionsListener != null && permissionsToExplain.size() > 0) {
            // show an explanation to the user asynchronously
            permissionsListener.onExplanationNeeded(permissionsToExplain);
        }

        ActivityCompat.requestPermissions(activity, permissions, PERMISSIONS_REQUEST_CODE);
    }

    /**
     * Call this method from activity onRequestPermissionsResult.
     *
     * @param requestCode  The request code passed in requestPermissions(android.app.Activity, String[], int)
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions which is either
     *                     PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE:
                if (permissionsListener != null) {
                    boolean granted = false;
                    for (int grantResult : grantResults) {
                        granted = grantResult == PackageManager.PERMISSION_GRANTED;
                        if (!granted) break;
                    }
                    permissionsListener.onPermissionResult(granted);
                }
                break;
            default:
                // Ignored
        }
    }
}
