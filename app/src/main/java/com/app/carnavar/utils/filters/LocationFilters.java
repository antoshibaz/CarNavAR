package com.app.carnavar.utils.filters;

import android.location.Location;

import com.app.carnavar.utils.android.TimeUtils;

public class LocationFilters {

    public static class GeoHeuristicFilter {

        public Location process(Location newLocation, Location lastBestLocation) {
            return null;
        }

        private long getLocationAge(Location location) {
            long currentTimeMillis = TimeUtils.currentAndroidSystemTimeNanos();
            long locationTimeMillis = location.getElapsedRealtimeNanos();
            return TimeUtils.nanos2millis(currentTimeMillis - locationTimeMillis);
        }
    }

    public static class GeoHashFilter {

    }
}
