package com.app.carnavar.utils.filters;

import android.location.Location;
import android.util.Log;

import com.app.carnavar.utils.android.TimeUtils;
import com.app.carnavar.utils.maps.MapsUtils;

public class LocationFilters {

    public static class GeoHeuristicFilter {

        private static final String TAG = GeoHeuristicFilter.class.getSimpleName();

        private float minUpdateDistanceMeters = 5.0f;
        private long ageUpdateTimeMillis = 30 * 1000; // | 30 sec
        private float speedForBearingStability = 3.0f; // m/s
        private float distanceAccuracyLimit = 50; // meters
        private float minSpeed = 3000f / (60 * 60); // m/sec | 3 km/h maybe for pedestrian
        private float maxSpeed = 200000f / (60 * 60); // m/sec | 150 km/h maybe for car
        private float patienceDeltaTimeSec = 3.0f;
        private float deltaAcc = 5.0f;

        private long bearingStabilityCounter = 0;
        private long bearingStabilityCountThresh = 6;
        private long bearingStabilityPatienceCounter = 0;
        private long bearingStabilityPatienceCountThresh = 3;

        private Location lastBestLocation;
        private boolean bearingIsStable = false;
        private boolean bearingIsEstablished = false;

        public Location process(Location newLocation) {
            // if last location is null
            if (lastBestLocation == null) {
                lastBestLocation = newLocation;
                return lastBestLocation;
            }

            // calc metrics
            double distance = MapsUtils.calcDistance(newLocation, lastBestLocation);
            long timeDiffMillis = TimeUtils.nanos2millis(newLocation.getElapsedRealtimeNanos()
                    - lastBestLocation.getElapsedRealtimeNanos());
            float lastLocAcc = lastBestLocation.getAccuracy();
            float newLocAcc = newLocation.getAccuracy();
            float lastLocSpeed = lastBestLocation.getSpeed();
            float newLocSpeed = newLocation.getSpeed();

            boolean newLocAccIsOk = newLocAcc < distanceAccuracyLimit;
            boolean newLocSpeedIsOk = newLocSpeed < maxSpeed;
            boolean newLocIsMoreAccurateThenLast = newLocAcc > lastLocAcc;
            boolean updDistanceIsMoreMin = distance > minUpdateDistanceMeters;
            boolean updDistanceIsOk = distance < distanceAccuracyLimit;
            boolean updSamplingIsSlow = TimeUtils.millis2sec(timeDiffMillis) > patienceDeltaTimeSec;
            boolean updAgeTimeIsOld = timeDiffMillis > ageUpdateTimeMillis;

            if (newLocAccIsOk && newLocSpeedIsOk) {
                if (updAgeTimeIsOld || (newLocIsMoreAccurateThenLast && Math.abs(newLocAcc - lastLocAcc) > deltaAcc)) {
                    // ret new loc
                    if (newLocSpeed > speedForBearingStability) {
                        bearingIsStable = true;
                    }
                    Log.d(TAG, "good upd | bear=" + bearingIsStable);
                    return retNewLocation(newLocation);
                } else {
                    if (!updDistanceIsMoreMin) { // and time diff is long
                        // speed is can be very small, bearing unstable - real location is not changed
                        float predictSpeedForDiffDist = (float) (distance / TimeUtils.millis2sec(timeDiffMillis));
                        if (predictSpeedForDiffDist < minSpeed) {
                            // ret last loc
                            bearingIsStable = false;
                            Log.d(TAG, "ret last | predSpeed=" + predictSpeedForDiffDist + " dist=" + distance + " bear=" + bearingIsStable);
                            return retLastLocation();
                        } else {
                            // ret new loc
                            if (newLocSpeed > speedForBearingStability) {
                                bearingIsStable = true;
                            }
                            Log.d(TAG, "ret new | predSpeed=" + predictSpeedForDiffDist + " dist=" + distance + " bear=" + bearingIsStable);
                            return retNewLocation(newLocation);
                        }
                    } else if (updSamplingIsSlow && updDistanceIsOk) {
                        // ret new loc
                        if (newLocSpeed > speedForBearingStability) {
                            bearingIsStable = true;
                        }
                        Log.d(TAG, "ret new | dist=" + distance + " bear=" + bearingIsStable);
                        return retNewLocation(newLocation);
                    } else if (!updSamplingIsSlow) {
                        float distMaxThresh = (float) (maxSpeed * TimeUtils.millis2sec(timeDiffMillis));
                        float predictDist = (float) (((lastLocSpeed + newLocSpeed) / 2) * TimeUtils.millis2sec(timeDiffMillis));
                        if (distance < (predictDist + distMaxThresh) / 2) {
                            // ret new
                            if (newLocSpeed > speedForBearingStability) {
                                bearingIsStable = true;
                            }
                            Log.d(TAG, "ret new | distMaxThresh=" + distMaxThresh + " predictDist=" + predictDist
                                    + " dist=" + distance + " bear=" + bearingIsStable);
                            return retNewLocation(newLocation);
                        } else {
                            // ret last
                            bearingIsStable = false;
                            Log.d(TAG, "ret last | distMaxThresh=" + distMaxThresh + " predictDist=" + predictDist
                                    + " dist=" + distance + " bear=" + bearingIsStable);
                            return retLastLocation();
                        }
                    } else {
                        // ret new
                        if (newLocSpeed > speedForBearingStability) {
                            bearingIsStable = true;
                        }
                        Log.d(TAG, "else ret new | bear=" + bearingIsStable);
                        return retNewLocation(newLocation);
                    }
                }
            } else {
                // ret last loc
                bearingIsStable = false;
                Log.d(TAG, "else ret last | bear=" + bearingIsStable);
                return retLastLocation();
            }
        }

        private Location retNewLocation(Location newLocation) {
            analyzeBearingStability();
            lastBestLocation = newLocation;
            return retLastLocation();
        }

        private Location retLastLocation() {
            analyzeBearingStability();
            return lastBestLocation;
        }

        private void analyzeBearingStability() {
            if (bearingIsStable()) {
                if (!bearingIsEstablished) {
                    bearingStabilityCounter++;
                    if (bearingStabilityCounter >= bearingStabilityCountThresh) {
                        bearingIsEstablished = true;
                    }
                } else {
                    bearingStabilityPatienceCounter -= 2;
                    if (bearingStabilityPatienceCounter < 0)
                        bearingStabilityPatienceCounter = 0;
                }
            } else {
                if (bearingIsEstablished) {
                    bearingStabilityPatienceCounter++;
                    if (bearingStabilityPatienceCounter >= bearingStabilityPatienceCountThresh) {
                        bearingIsEstablished = false;
                        bearingStabilityPatienceCounter = 0;
                        bearingStabilityCounter = 0;
                    }
                } else {
                    bearingStabilityCounter -= 2;
                    if (bearingStabilityCounter < 0) bearingStabilityCounter = 0;
                }
            }
        }

        private boolean bearingIsStable() {
            return bearingIsStable;
        }

        public boolean bearingIsEstablished() {
            return bearingIsEstablished;
        }
    }

    public static class GeoHashFilter {

    }
}
