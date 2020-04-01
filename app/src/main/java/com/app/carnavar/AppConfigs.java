package com.app.carnavar;

import android.Manifest;

public class AppConfigs {

    public static final String[] APP_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION, // Manifest.permission.ACCESS_COARSE_LOCATION
            Manifest.permission.INTERNET,
            Manifest.permission.CAMERA
    };
}
