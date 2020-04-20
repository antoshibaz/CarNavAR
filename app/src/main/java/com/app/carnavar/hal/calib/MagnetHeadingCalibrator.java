package com.app.carnavar.hal.calib;

import android.hardware.GeomagneticField;
import android.location.Location;
import android.util.Log;

import com.app.carnavar.utils.math.Matrix2;

/**
 * Class for calibration heading/bearing/azimuth from magnetometer and gps data
 * based on paper:
 * https://www.researchgate.net/publication/266082960_Magnetometer_Calibration_for_Portable_Navigation_Devices_in_Vehicles_Using_a_Fast_and_Autonomous_Technique
 * other useful papers:
 * https://hal.inria.fr/hal-01376745/document
 * http://www.robesafe.com/personal/javier.yebes/docs/Almazan13iv.pdf
 * https://arxiv.org/pdf/1811.01065.pdf
 */
public class MagnetHeadingCalibrator {

    public static final String TAG = MagnetHeadingCalibrator.class.getSimpleName();
    public static final int NONE = -1;

    private int nSamples;
    private boolean isCalibSuccess = false;
    private boolean isReadyForCalib = false;
    private GeomagneticField geomagneticField;
    private double bx;
    private double by;
    private double sx;
    private double sy;

    private Matrix2 x1Res = new Matrix2(2, 1);
    private Matrix2 x2Res = new Matrix2(2, 1);
    private double[] extGpsBearings;
    private double[] hxArr;
    private double[] hyArr;
    private double hh;
    private int currentSample = 0;

    private CalibStatusCallback calibStatusCallback;

    public interface CalibStatusCallback {
        void samplesIsCollected();

        void calibIsSuccessCompleted();
    }

    public void setCalibStatusCallback(CalibStatusCallback calibStatusCallback) {
        this.calibStatusCallback = calibStatusCallback;
    }

    public MagnetHeadingCalibrator(int samplesCount, CalibStatusCallback calibStatusCallback) {
        this.calibStatusCallback = calibStatusCallback;
        nSamples = samplesCount;
        extGpsBearings = new double[nSamples];
        hxArr = new double[nSamples];
        hyArr = new double[nSamples];
    }

    public boolean addSample(Location location, double[] magnetValues) {
        if (currentSample >= nSamples) {
            return false;
        }

        geomagneticField = new GeomagneticField(
                (float) location.getLatitude(),
                (float) location.getLongitude(),
                (float) location.getAltitude(),
                location.getTime());
        double bearingPhiExt = ((Math.random() < 0.5) ? (359 + Math.random() * 0.9) : Math.random() * 1);
        Log.d(TAG, "real bear=" + bearingPhiExt);
        bearingPhiExt = ((bearingPhiExt - geomagneticField.getDeclination()) + 360f) % 360f;
        if (hh == 0) {
            hh = geomagneticField.getHorizontalStrength() / 1000;
        }

        extGpsBearings[currentSample] = bearingPhiExt;
        hxArr[currentSample] = magnetValues[0];
        hyArr[currentSample] = magnetValues[1];
        Log.d(TAG, "added to calib -> bear=" + bearingPhiExt
                + " hx=" + magnetValues[0]
                + " hy=" + magnetValues[1]
                + " hh=" + hh);

        currentSample++;
        if (currentSample >= nSamples) {
            isReadyForCalib = true;
            if (calibStatusCallback != null) {
                calibStatusCallback.samplesIsCollected();
            }
        }

        return true;
    }

    public boolean calib() {
        if (isReadyForCalib && computeCalib(hxArr, hyArr, hh, extGpsBearings, x1Res, x2Res)) {
            // compute new calibration params
            double sx = 1.0f / x1Res.data[0][0]; // 1/A
            double bx = x1Res.data[1][0] / x1Res.data[0][0]; // B/A
            double sy = 1.0f / x2Res.data[0][0]; // 1/C
            double by = x2Res.data[1][0] / x2Res.data[0][0]; // D/C

            isReadyForCalib = false;
            currentSample = 0;
            if (checkCalib(sx, sy, bx, by)) { // if new calib is ok
                if (!isCalibSuccess) {
                    isCalibSuccess = true;
                    this.sx = sx;
                    this.sy = sy;
                    this.bx = bx;
                    this.by = by;

                    Log.d(TAG, "new calib -> sx=" + sx + " sy=" + sy + " bx=" + bx + " by=" + by);
                    if (calibStatusCallback != null) {
                        calibStatusCallback.calibIsSuccessCompleted();
                    }
                } else {
                    // compare prev and new calib precision
                    double prevCalibMAE = 0;
                    double newCalibMAE = 0;
                    for (int i = 0; i < extGpsBearings.length; i++) {
                        prevCalibMAE += Math.abs(computeCalibHeading(new double[]{hxArr[i], hyArr[i]},
                                this.sx, this.sy, this.bx, this.by) - extGpsBearings[i]);
                        newCalibMAE += Math.abs(computeCalibHeading(new double[]{hxArr[i], hyArr[i]},
                                sx, sy, bx, by) - extGpsBearings[i]);
                    }
                    prevCalibMAE /= extGpsBearings.length;
                    newCalibMAE /= extGpsBearings.length;

                    if (newCalibMAE >= prevCalibMAE) {
                        this.sx = sx;
                        this.sy = sy;
                        this.bx = bx;
                        this.by = by;

                        Log.d(TAG, "replace calib -> sx=" + sx + " sy=" + sy + " bx=" + bx + " by=" + by);
                        if (calibStatusCallback != null) {
                            calibStatusCallback.calibIsSuccessCompleted();
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean checkCalib(double sx, double sy, double bx, double by) {
        double gtExtGpsBearing = mean(extGpsBearings);
        double meanHx = mean(hxArr);
        double meanHy = mean(hyArr);
        double predBearing = computeCalibHeading(new double[]{meanHx, meanHy}, sx, sy, bx, by);
        double calibMx = (meanHx - bx) / sx;
        double calibMy = (meanHy - by) / sy;
        Log.d(TAG, "check calib -> gtBear=" + gtExtGpsBearing + " predBear=" + predBearing
                + " calibMx=" + calibMx + " calibMy=" + calibMy);
        if (Math.abs(gtExtGpsBearing - predBearing) < 20 || Math.abs(gtExtGpsBearing - 360 - predBearing) < 20) {
            return true;
        }
        return false;
    }

    public void killCalib() {
        isReadyForCalib = false;
        currentSample = 0;
        isCalibSuccess = false;
    }

    public double getCalibHeading(double[] magnetValues, boolean withDeclination) {
        if (isCalibSuccess) {
            double calibBearing = computeCalibHeading(magnetValues, sx, sy, bx, by);
            if (withDeclination) {
                calibBearing += geomagneticField.getDeclination();
            }
            calibBearing = (calibBearing + 360f) % 360f;
            return calibBearing;
        } else {
            return NONE;
        }
    }

    private static double computeCalibHeading(double[] magnetValues,
                                              double sx, double sy, double bx, double by) {
        double calibMx = (magnetValues[0] - bx) / sx;
        double calibMy = (magnetValues[1] - by) / sy;
        double offset = 0;
        if (calibMx < 0) {
            offset = -180;
        }

        Log.d(TAG, "atan=" + Math.toDegrees(Math.atan2(calibMy, calibMx))
                + " atan_gt=" + (Math.toDegrees(Math.atan2(magnetValues[1], magnetValues[0])) + 360f) % 360);
        double calibBearing = Math.toDegrees(Math.atan2(calibMy, calibMx)) + offset;
        calibBearing = (calibBearing + 360f) % 360f;
        return calibBearing;
    }

    private static double mean(double[] vals) {
        double mean = 0;
        for (int i = 0; i < vals.length; i++) {
            mean += vals[i];
        }
        return mean / vals.length;
    }

    // remapping axes for magnet values
//            switch (windowManager.getDefaultDisplay().getRotation()) {
//            case Surface.ROTATION_90:
//            case Surface.ROTATION_270:
//                hdx = magnetValues[1];
//                hdy = magnetValues[2];
//                hdz = magnetValues[0];
//                break;
//            case Surface.ROTATION_180:
//            case Surface.ROTATION_0:
//            default:
//                hdx = magnetValues[0];
//                hdy = magnetValues[2];
//                hdz = magnetValues[1];
//                break;

    private static boolean computeCalib(double[] hxArr, double[] hyArr, double hh, double[] extGpsBearings,
                                        Matrix2 x1Res, Matrix2 x2Res) {
        if (hxArr.length != extGpsBearings.length
                || hyArr.length != extGpsBearings.length) {
            return false;
        }
        if (x1Res == null || x2Res == null) {
            return false;
        }

        Matrix2 h1 = new Matrix2(hxArr.length, 2);
        Matrix2 h2 = new Matrix2(hyArr.length, 2);
        double kh1 = 1 / hh;
        double kh2 = -1 / hh;
        for (int i = 0; i < hxArr.length; i++) {
            h1.data[i][0] = hxArr[i] * kh1;
            h1.data[i][1] = -1 * kh1;
            h2.data[i][0] = hyArr[i] * kh2;
            h2.data[i][1] = -1 * kh2;
        }

        Matrix2 y1 = new Matrix2(extGpsBearings.length, 1);
        Matrix2 y2 = new Matrix2(extGpsBearings.length, 1);
        for (int i = 0; i < extGpsBearings.length; i++) {
            y1.data[i][0] = Math.cos(Math.toRadians(extGpsBearings[i])); // TODO: may be bear [0..360]->[-180..180]?
            y2.data[i][0] = Math.sin(Math.toRadians(extGpsBearings[i]));
        }

        Matrix2 h1T = new Matrix2(2, hxArr.length);
        Matrix2 h2T = new Matrix2(2, hyArr.length);
        Matrix2.matrixTranspose(h1, h1T);
        Matrix2.matrixTranspose(h2, h2T);

        Matrix2 mul1 = new Matrix2(2, 2);
        Matrix2 mul2 = new Matrix2(2, 2);
        Matrix2 mul1inv = new Matrix2(2, 2);
        Matrix2 mul2inv = new Matrix2(2, 2);
        Matrix2.matrixMultiply(h1T, h1, mul1);
        Matrix2.matrixMultiply(h2T, h2, mul2);
        if (!Matrix2.matrixDestructiveInvert(mul1, mul1inv)) {
            return false;
        }
        if (!Matrix2.matrixDestructiveInvert(mul2, mul2inv)) {
            return false;
        }

        Matrix2 y1Mul = new Matrix2(2, hxArr.length);
        Matrix2 y2Mul = new Matrix2(2, hyArr.length);
        Matrix2.matrixMultiply(mul1inv, h1T, y1Mul);
        Matrix2.matrixMultiply(mul2inv, h2T, y2Mul);
        Matrix2.matrixMultiply(y1Mul, y1, x1Res);
        Matrix2.matrixMultiply(y2Mul, y2, x2Res);

        return true;
    }
}
