package com.app.carnavar;

import android.Manifest;

import com.app.carnavar.maps.NavMapRoute;

import java.util.Arrays;
import java.util.List;

public class AppConfigs {

    public static final String[] APP_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION, // Manifest.permission.ACCESS_COARSE_LOCATION
            Manifest.permission.INTERNET,
            Manifest.permission.CAMERA
    };

    public static final List<NavMapRoute.ManeuverType> MANEUVER_TYPES = Arrays.asList(
            NavMapRoute.ManeuverType.TURN,
            NavMapRoute.ManeuverType.MERGE,
            NavMapRoute.ManeuverType.OFF_RAMP,
            NavMapRoute.ManeuverType.ON_RAMP,
            NavMapRoute.ManeuverType.FORK,
            NavMapRoute.ManeuverType.CONTINUE
    );

    public static void initStaticLibs() {

    }
}
