package com.app.carnavar.hal.location;

import com.app.carnavar.utils.filters.KalmanFilter;
import com.app.carnavar.utils.math.Matrix2;

public class KalmanFusionGpsImu {

    public static final String TAG = KalmanFusionGpsImu.class.getSimpleName();

    private KalmanFilter kalmanFilter;
    private KalmanOptions kalmanOptions;

    private int measureDimension, stateDimension, controlDimension;

    private double timestampMsPredict;
    private double timestampMsUpdate;
    private int predictCount;

    public static class KalmanOptions {

        private boolean useGpsSpeed = false;
        private double accelerationDeviance = 0.1f;
        private double velocityMulFactor = 1.0f;
        private double positionMulFactor = 1.0f;

        public KalmanOptions() {
        }

        public static KalmanOptions Options() {
            return new KalmanOptions();
        }

        public KalmanOptions useGpsSpeed(boolean useGpsSpeed) {
            this.useGpsSpeed = useGpsSpeed;
            return this;
        }

        public KalmanOptions setAccelerationDeviance(double accelerationDeviance) {
            this.accelerationDeviance = accelerationDeviance;
            return this;
        }

        public KalmanOptions setVelocityVarianceMulFactor(double velocityMulFactor) {
            this.velocityMulFactor = velocityMulFactor;
            return this;
        }

        public KalmanOptions setPositionVarianceMulFactor(double positionMulFactor) {
            this.positionMulFactor = positionMulFactor;
            return this;
        }
    }

    public KalmanFusionGpsImu(KalmanOptions kalmanOptions) {
        this.kalmanOptions = kalmanOptions;
        measureDimension = this.kalmanOptions.useGpsSpeed ? 4 : 2;
        stateDimension = 4;
        controlDimension = 2;
        kalmanFilter = new KalmanFilter(stateDimension, measureDimension, controlDimension);
    }

    // x, y - meters; xVelocity, yVelocity - meters per sec; timestampMs = milliseconds
    public void init(double x, double y, double xVelocity, double yVelocity, double positionVariance,
                     double timestampMs) {
        timestampMsPredict = timestampMsUpdate = timestampMs;
        predictCount = 0;
        kalmanFilter.Xk_k.setData(x, y, xVelocity, yVelocity);
        kalmanFilter.H.setIdentityDiag(); //state has 4d and measurement has 4d too. so here is identity
        kalmanFilter.Pk_k.setIdentity();
        kalmanFilter.Pk_k.scale(positionVariance);
    }

    public void predict(double xAcceleration, double yAcceleration, double timestampNowMs) {
        double dtPredict = (timestampNowMs - timestampMsPredict) / 1000.0; // convert to sec
        double dtUpdate = (timestampNowMs - timestampMsUpdate) / 1000.0;
        rebuildF(dtPredict);
        rebuildB(dtPredict);
        rebuildU(xAcceleration, yAcceleration);

        ++predictCount;
        rebuildQ(dtUpdate, kalmanOptions.accelerationDeviance);

        timestampMsPredict = timestampNowMs;
        kalmanFilter.predict();
        Matrix2.matrixCopy(kalmanFilter.Xk_km1, kalmanFilter.Xk_k);
    }

    public void update(double x, double y, double xVel, double yVel,
                       double positionVariance, double velocityVariance, double timestampMs) {
        predictCount = 0;
        timestampMsUpdate = timestampMs;
        rebuildR(positionVariance, velocityVariance);
        kalmanFilter.Zk.setData(x, y, xVel, yVel);
        kalmanFilter.update();
    }

    public double getCurrentX() {
        return kalmanFilter.Xk_k.data[0][0];
    }

    public double getCurrentY() {
        return kalmanFilter.Xk_k.data[1][0];
    }

    public double getCurrentXVel() {
        return kalmanFilter.Xk_k.data[2][0];
    }

    public double getCurrentYVel() {
        return kalmanFilter.Xk_k.data[3][0];
    }

    private void rebuildF(double dtPredict) {
        double[] f = {
                1.0, 0.0, dtPredict, 0.0,
                0.0, 1.0, 0.0, dtPredict,
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0
        };
        kalmanFilter.F.setData(f);
    }

    private void rebuildU(double xAcceleration, double yAcceleration) {
        kalmanFilter.Uk.setData(xAcceleration, yAcceleration);
    }

    private void rebuildB(double dtPredict) {
        double dt2 = 0.5 * dtPredict * dtPredict; // t^2/2
        double[] b = {
                dt2, 0.0,
                0.0, dt2,
                dtPredict, 0.0,
                0.0, dtPredict
        };
        kalmanFilter.B.setData(b);
    }

    private void rebuildR(double positionVariance, double velocityVariance) {
        positionVariance *= kalmanOptions.positionMulFactor;
        velocityVariance *= kalmanOptions.velocityMulFactor;

        if (kalmanOptions.useGpsSpeed) {
            double[] R = {
                    positionVariance, 0.0, 0.0, 0.0,
                    0.0, positionVariance, 0.0, 0.0,
                    0.0, 0.0, velocityVariance, 0.0,
                    0.0, 0.0, 0.0, velocityVariance
            };
            kalmanFilter.R.setData(R);
        } else {
            kalmanFilter.R.setIdentity();
            kalmanFilter.R.scale(positionVariance);
        }
    }

    private void rebuildQ(double accelerationDeviance, double dtUpdate) {
//        now we use predictCount. but maybe there is way to use dtUpdate.
//        m_kf.Q.setIdentity();
//        m_kf.Q.scale(accSigma * dtUpdate);
        double velDeviance = accelerationDeviance * predictCount;
        double posDeviance = velDeviance * predictCount / 2;
        double covariance = velDeviance * posDeviance;

        double posVariance = posDeviance * posDeviance;
        double velVariance = velDeviance * velDeviance;

        double[] Q = {
                posVariance, 0.0, covariance, 0.0,
                0.0, posVariance, 0.0, covariance,
                covariance, 0.0, velVariance, 0.0,
                0.0, covariance, 0.0, velVariance
        };
        kalmanFilter.Q.setData(Q);
    }
}
