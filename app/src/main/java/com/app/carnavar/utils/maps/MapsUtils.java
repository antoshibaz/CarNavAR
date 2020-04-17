package com.app.carnavar.utils.maps;

import android.location.Location;

import java.util.List;

import static java.lang.Math.PI;
import static java.lang.Math.asin;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

// https://www.movable-type.co.uk/scripts/latlong.html
public class MapsUtils {

    // latitude: [-90 ... 90] degrees of Source Pole (Equator)
    // longitude: [-180 ... 180] degrees of Prime Meridian
    // latitude and longitude in spherical coordinate system

    // mean Earth radius in meters
    public static final double EARTH_RADIUS = 6371009;

    // bearing from src to dst locations in range 0 <= bearing <= 360 degrees
    public static double calcBearing(Location src, Location dst) {
        double srcLat = src.getLatitude();
        double srcLng = src.getLongitude();
        double dstLat = dst.getLatitude();
        double dstLng = dst.getLongitude();

        return calcBearing(srcLat, srcLng, dstLat, dstLng);
    }

    public static double calcBearing(double srcLat, double srcLng, double dstLat, double dstLng) {
        srcLat = toRadians(srcLat);
        srcLng = toRadians(srcLng);
        dstLat = toRadians(dstLat);
        dstLng = toRadians(dstLng);

        double lngDiff = dstLng - srcLng;
        double y = sin(lngDiff) * cos(dstLat);
        double x = cos(srcLat) * sin(dstLat) - sin(srcLat) * cos(dstLat) * cos(lngDiff);
        double bearing = toDegrees(atan2(y, x));

        return (bearing + 360) % 360;
    }

    // distance between src and dst locations
    public static double calcDistance(Location src, Location dst) {
        double srcLat = src.getLatitude();
        double srcLng = src.getLongitude();
        double dstLat = dst.getLatitude();
        double dstLng = dst.getLongitude();

        return haversineDistance(srcLat, srcLng, dstLat, dstLng);
    }

    public static double calcDistance3d(Location src, Location dst) {
        double srcLat = src.getLatitude();
        double srcLng = src.getLongitude();
        double srcAlt = dst.getAltitude();
        double dstLat = dst.getLatitude();
        double dstLng = dst.getLongitude();
        double dstAlt = dst.getAltitude();

        return haversineDistance3d(srcLat, srcLng, srcAlt, dstLat, dstLng, dstAlt);
    }

    // Earth model as plane - high precision on small distances and low precision on high distances
    public static double euclideanPythagoreanDistance(double srcLat, double srcLng, double dstLat, double dstLng) {
        srcLat = toRadians(srcLat);
        srcLng = toRadians(srcLng);
        dstLat = toRadians(dstLat);
        dstLng = toRadians(dstLng);

        double x = (dstLng - srcLng) * cos(0.5 * (dstLat + srcLat));
        double y = dstLat - srcLat;
        return EARTH_RADIUS * sqrt(x * x + y * y);
    }

    // Earth model as spheroid - high precision on all distances
    public static double haversineDistance(double srcLat, double srcLng, double dstLat, double dstLng) {
        srcLat = toRadians(srcLat);
        srcLng = toRadians(srcLng);
        dstLat = toRadians(dstLat);
        dstLng = toRadians(dstLng);

        double latDiff = dstLat - srcLat;
        double lngDiff = dstLng - srcLng;
        double a = pow(sin(latDiff / 2), 2) + pow(sin(lngDiff / 2), 2) * cos(srcLat) * cos(dstLat);
        //double c = 2 * atan2(sqrt(a), sqrt(1 - a));
        double c = 2 * asin(sqrt(a)); // more numerical stability around 0

        return EARTH_RADIUS * c;
    }

    public static double haversineDistance3d(double srcLat, double srcLng, double srcAlt,
                                             double dstLat, double dstLng, double dstAlt) {
        srcAlt = toRadians(srcAlt);
        dstAlt = toRadians(dstAlt);

        double greatCircleDistance = haversineDistance(srcLat, srcLng, dstLat, dstLng);
        return sqrt(pow(greatCircleDistance, 2) + pow((dstAlt - srcAlt), 2));
    }

    // Earth model as ellipsoid - very high precision on all distances
    public static double vincentyDistance(double srcLat, double srcLng, double dstLat, double dstLng) {
        return 0.0D;
    }

    public static Location getDstLocationByBearingAndDistance(Location srcLocation, double bearing, double distance) {
        double srcLat = srcLocation.getLatitude();
        double srcLng = srcLocation.getLongitude();
        double[] dstLatLng = getDstLocationByBearingAndDistance(srcLat, srcLng, bearing, distance);
        Location dstLocation = new Location(srcLocation.getProvider());
        dstLocation.setLatitude(dstLatLng[0]);
        dstLocation.setLongitude(dstLatLng[0]);
        return dstLocation;
    }

    public static Location getDstLocationByBearingAndDistance(Location srcLocation, double distance) {
        return getDstLocationByBearingAndDistance(srcLocation, srcLocation.getBearing(), distance);
    }

    public static double[] getDstLocationByBearingAndDistance(double srcLat, double srcLng, double bearing, double distance) {
        double angularDistance = distance / EARTH_RADIUS;
        bearing = toRadians(bearing);
        srcLat = toRadians(srcLat);
        srcLng = toRadians(srcLng);

        double dstLat = asin(sin(srcLat) * cos(angularDistance) + cos(srcLat) * sin(angularDistance) * cos(bearing));
        double dstLng = srcLng + atan2(sin(bearing) * sin(angularDistance) * cos(srcLat), cos(angularDistance) - sin(srcLat) * sin(dstLat));
        dstLng = (dstLng + 3 * PI) % (2 * PI) - PI; // normalize to -180 - + 180 degrees

        return new double[]{toDegrees(dstLat), toDegrees(dstLng)};
    }

    /**
     * Returns the location of origin when provided with a LatLng destination,
     * meters travelled and original bearing. Headings are expressed in degrees
     * clockwise from North. This function returns null when no solution is
     * available.
     *
     * @param dstLocation The destination LatLng.
     * @param bearing     The bearing in degrees clockwise from north.
     * @param distance    The distance travelled, in meters.
     */
    public static Location getSrcLocationByBearingAndDistance(Location dstLocation, double bearing, double distance) {
        double srcLat = dstLocation.getLatitude();
        double srcLng = dstLocation.getLongitude();
        double[] srcLatLng = getSrcLocationByBearingAndDistance(srcLat, srcLng, bearing, distance);
        if (srcLatLng != null) {
            Location srcLocation = new Location(dstLocation.getProvider());
            srcLocation.setLatitude(srcLatLng[0]);
            srcLocation.setLongitude(srcLatLng[1]);
            return srcLocation;
        }
        return null;
    }

    public static double[] getSrcLocationByBearingAndDistance(double dstLat, double dstLng, double bearing, double distance) {
        bearing = toRadians(bearing);
        distance /= EARTH_RADIUS;
        // http://lists.maptools.org/pipermail/proj/2008-October/003939.html
        double n1 = cos(distance);
        double n2 = sin(distance) * cos(bearing);
        double n3 = sin(distance) * sin(bearing);
        double n4 = sin(toRadians(dstLat));
        // There are two solutions for b. b = n2 * n4 +/- sqrt(), one solution results
        // in the latitude outside the [-90, 90] range. We first try one solution and
        // back off to the other if we are outside that range.
        double n12 = n1 * n1;
        double discriminant = n2 * n2 * n12 + n12 * n12 - n12 * n4 * n4;
        if (discriminant < 0) {
            // No real solution which would make sense in LatLng-space.
            return null;
        }
        double b = n2 * n4 + sqrt(discriminant);
        b /= n1 * n1 + n2 * n2;
        double a = (n4 - n2 * b) / n1;
        double fromLatRadians = atan2(a, b);
        if (fromLatRadians < -PI / 2 || fromLatRadians > PI / 2) {
            b = n2 * n4 - sqrt(discriminant);
            b /= n1 * n1 + n2 * n2;
            fromLatRadians = atan2(a, b);
        }
        if (fromLatRadians < -PI / 2 || fromLatRadians > PI / 2) {
            // No solution which would make sense in LatLng-space.
            return null;
        }
        double fromLngRadians = toRadians(dstLng) -
                atan2(n3, n1 * cos(fromLatRadians) - n2 * sin(fromLatRadians));

        return new double[]{toDegrees(fromLatRadians), toDegrees(fromLngRadians)};
    }

    /**
     * Returns the LatLng which lies the given fraction of the way between the
     * origin LatLng and the destination LatLng.
     *
     * @param from     The LatLng from which to start.
     * @param to       The LatLng toward which to travel.
     * @param fraction A fraction of the distance to travel [0.0 - 1.0].
     * @return The interpolated LatLng.
     */
    public static Location interpolate(Location from, Location to, double fraction) {
        double fromLat = from.getLatitude();
        double fromLng = from.getLongitude();
        double toLat = to.getLatitude();
        double toLng = to.getLongitude();

        // location-based interpolation
        double[] interpolLatLng = slerp(fromLat, fromLng, toLat, toLng, fraction);
        Location interpolLocation = new Location(from.getProvider());
        interpolLocation.setLatitude(interpolLatLng[0]);
        interpolLocation.setLatitude(interpolLatLng[0]);

        // time-based linear interpolation + speed linear interpolation
//        long t1 = location1.getTimeStamp(); // in milliseconds;
//        long t2 = location2.getTimeStamp();
//        double deltaLat = location2.latitude - location1.latitude;
//        doule deltaLon =  location2.longitude- location1.longtude;
//        // remove this line if you don't have measured speed:
//        double deltaSpeed =  location2.speed - location1.speed;
//
//        long step = 1 * 1000; // 1 second in millis
//        for (long t = t1; t1 < t2; t+= step) {
//
//            // t0_1 shall run from 0.0 to (nearly) 1.0 in that loop
//            double t0_1 = (t - t1) / (t2 - t1);
//            double latInter = lat1 + deltaLat  * t0_1;
//            double lonInter = lon1 + deltaLon  * t0_1;
//            // remove the line below if you dont have speed
//            double speedInter = speed1 + deltaSpeed  * t0_1;
//            Location interPolLocation = new Location(latInter, lonInter, speedInter);
//            // add interPolLocation to list or plot.
//        }

        // + time linear interpolation
//        Lat_to_Travel  = CurLat - TargetLat
//        Long_to_Travel = CurLong - TargetLong
//        Time_to_Travel = ETA - now
//        NbOfIntermediates       = 10  // for example
//        Lat_at_Intermediate(n)  = CurLat + (1/NbOfIntermediates * Lat_to_travel)
//        Long_at_Intermediate(n) = CurLong + (1/NbOfIntermediates * Long_to_travel)
//        Time_at_Intermediate(n) = now + (1/NbOfIntermediates * Time_to_travel)

        // kinematic interpolation

        return interpolLocation;
    }

    public static double[] slerp(double fromLat, double fromLng,
                                 double toLat, double toLng,
                                 double fraction) {
        // http://en.wikipedia.org/wiki/Slerp
        fromLat = toRadians(fromLat);
        fromLng = toRadians(fromLng);
        toLat = toRadians(toLat);
        toLng = toRadians(toLng);
        double cosFromLat = cos(fromLat);
        double cosToLat = cos(toLat);

        // Computes Linear Spherical interpolation coefficients.
        double angle = computeAngleBetween(fromLat, fromLng, toLat, toLng);
        double sinAngle = sin(angle);
        if (sinAngle < 1E-6) { // angle is very small because apply simple linear interpolation
            return new double[]{fromLat + fraction * (toLat - fromLat),
                    fromLng + fraction * (toLng - fromLng)};
        }
        double a = sin((1 - fraction) * angle) / sinAngle;
        double b = sin(fraction * angle) / sinAngle;

        // Converts from polar to vector and interpolate.
        double x = a * cosFromLat * cos(fromLng) + b * cosToLat * cos(toLng);
        double y = a * cosFromLat * sin(fromLng) + b * cosToLat * sin(toLng);
        double z = a * sin(fromLat) + b * sin(toLat);

        // Converts interpolated vector back to polar.
        double lat = atan2(z, sqrt(x * x + y * y));
        double lng = atan2(y, x);
        return new double[]{toDegrees(lat), toDegrees(lng)};
    }

    /**
     * Returns haversine(angle-in-radians).
     * hav(x) == (1 - cos(x)) / 2 == sin(x / 2)^2.
     */
    private static double hav(double x) {
        double sinHalf = sin(x * 0.5);
        return sinHalf * sinHalf;
    }

    /**
     * Computes inverse haversine. Has good numerical stability around 0.
     * arcHav(x) == acos(1 - 2 * x) == 2 * asin(sqrt(x)).
     * The argument must be in [0, 1], and the result is positive.
     */
    private static double arcHav(double x) {
        return 2 * asin(sqrt(x));
    }

    // Given h==hav(x), returns sin(abs(x)).
    private static double sinFromHav(double h) {
        return 2 * sqrt(h * (1 - h));
    }

    // Returns hav(asin(x)).
    private static double havFromSin(double x) {
        double x2 = x * x;
        return x2 / (1 + sqrt(1 - x2)) * .5;
    }

    // Returns sin(arcHav(x) + arcHav(y)).
    private static double sinSumFromHav(double x, double y) {
        double a = sqrt(x * (1 - x));
        double b = sqrt(y * (1 - y));
        return 2 * (a + b - 2 * (a * y + b * x));
    }

    /**
     * Returns hav() of distance from (lat1, lng1) to (lat2, lng2) on the unit sphere.
     */
    private static double havDistance(double lat1, double lat2, double dLng) {
        return hav(lat1 - lat2) + hav(dLng) * cos(lat1) * cos(lat2);
    }

    /**
     * Returns distance on the unit sphere; the arguments are in radians.
     */
    private static double distanceRadians(double lat1, double lng1, double lat2, double lng2) {
        return arcHav(havDistance(lat1, lat2, lng1 - lng2));
    }

    /**
     * Returns the angle between two LatLngs, in radians. This is the same as the distance
     * on the unit sphere.
     */
    private static double computeAngleBetween(double srcLat, double srcLng, double dstLat, double dstLng) {
        return distanceRadians(toRadians(srcLat), toRadians(srcLng),
                toRadians(dstLat), toRadians(dstLng));
    }

    /**
     * Returns the distance between two LatLng points, in meters.
     */
    public static double computeDistanceBetween(double srcLat, double srcLng, double dstLat, double dstLng) {
        return computeAngleBetween(srcLat, srcLng, dstLat, dstLng) * EARTH_RADIUS;
    }

    public static double longitudeToMeters(double lng) {
        double distance = computeDistanceBetween(0.0, lng, 0.0, 0.0);
        return distance * (lng < 0.0 ? -1.0 : 1.0);
    }

    public static double latitudeToMeters(double lat) {
        double distance = computeDistanceBetween(lat, 0.0, 0.0, 0.0);
        return distance * (lat < 0.0 ? -1.0 : 1.0);
    }

    public static double[] getLatLngByDistances(double xMetersToLng, double yMetersToLat) {
        double zeroLat = 0.0f, zeroLng = 0.0f;
        double[] eastLocation = MapsUtils.getDstLocationByBearingAndDistance(zeroLat, zeroLng,
                90.0f, xMetersToLng);
        double[] northEastLocation = MapsUtils.getDstLocationByBearingAndDistance(eastLocation[0], eastLocation[1],
                0.0f, yMetersToLat);
        return northEastLocation;
    }

    /**
     * Returns the length of the given path, in meters, on Earth.
     */
    public static double computePathLength(List<double[]> path) {
        if (path.size() < 2) {
            return 0;
        }
        double length = 0;
        double[] prevLatLng = path.get(0);
        double prevLat = toRadians(prevLatLng[0]);
        double prevLng = toRadians(prevLatLng[1]);
        for (double[] pointLatLng : path) {
            double lat = toRadians(pointLatLng[0]);
            double lng = toRadians(pointLatLng[1]);
            length += distanceRadians(prevLat, prevLng, lat, lng);
            prevLat = lat;
            prevLng = lng;
        }
        return length * EARTH_RADIUS;
    }

    public static String toString(Location location) {
        return "Lat=" + location.getLatitude() + " Lng=" + location.getLongitude() + " Alt=" + location.getAltitude() +
                " Acc=" + location.getAccuracy() + " AltAcc=" + location.getVerticalAccuracyMeters() +
                " Bear=" + location.getBearing() + " BearAcc=" + location.getBearingAccuracyDegrees() +
                " Speed=" + location.getSpeed() + " SpeedAcc=" + location.getSpeedAccuracyMetersPerSecond() +
                " SystemFixTime=" + location.getElapsedRealtimeNanos() + " FixTimestamp=" + location.getTime();
    }
}
