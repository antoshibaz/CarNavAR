package com.app.carnavar.utils.filters;

import com.app.carnavar.utils.math.Matrix2;

/**
 * Created by lezh1k on 2/13/18.
 */

public class KalmanFilter {
    /*these matrices should be provided by user*/
    public Matrix2 F; // state transition atriodel
    public Matrix2 H; // observation model
    public Matrix2 B; // control Matrix
    public Matrix2 Q; // process noise covariance
    public Matrix2 R; // observation noise covariance

    /*these matrices will be updated by user*/
    public Matrix2 Uk; //control vector
    public Matrix2 Zk; //actual values (measured)
    public Matrix2 Xk_km1; //predicted state estimate
    public Matrix2 Pk_km1; //predicted estimate covariance
    public Matrix2 Yk; //measurement innovation

    public Matrix2 Sk; //innovation covariance
    public Matrix2 SkInv; //innovation covariance inverse

    public Matrix2 K; //Kalman gain (optimal)
    public Matrix2 Xk_k; //updated (current) state
    public Matrix2 Pk_k; //updated estimate covariance
    public Matrix2 Yk_k; //post fit residual

    /*auxiliary matrices*/
    public Matrix2 auxBxU;
    public Matrix2 auxSDxSD;
    public Matrix2 auxSDxMD;

    public KalmanFilter(int stateDimension,
                        int measureDimension,
                        int controlDimension) {
        this.F = new Matrix2(stateDimension, stateDimension);
        this.H = new Matrix2(measureDimension, stateDimension);
        this.Q = new Matrix2(stateDimension, stateDimension);
        this.R = new Matrix2(measureDimension, measureDimension);

        this.B = new Matrix2(stateDimension, controlDimension);
        this.Uk = new Matrix2(controlDimension, 1);

        this.Zk = new Matrix2(measureDimension, 1);

        this.Xk_km1 = new Matrix2(stateDimension, 1);
        this.Pk_km1 = new Matrix2(stateDimension, stateDimension);

        this.Yk = new Matrix2(measureDimension, 1);
        this.Sk = new Matrix2(measureDimension, measureDimension);
        this.SkInv = new Matrix2(measureDimension, measureDimension);

        this.K = new Matrix2(stateDimension, measureDimension);

        this.Xk_k = new Matrix2(stateDimension, 1);
        this.Pk_k = new Matrix2(stateDimension, stateDimension);
        this.Yk_k = new Matrix2(measureDimension, 1);

        this.auxBxU = new Matrix2(stateDimension, 1);
        this.auxSDxSD = new Matrix2(stateDimension, stateDimension);
        this.auxSDxMD = new Matrix2(stateDimension, measureDimension);
    }

    public void predict() {
        //Xk|k-1 = Fk*Xk-1|k-1 + Bk*Uk
        Matrix2.matrixMultiply(F, Xk_k, Xk_km1);
        Matrix2.matrixMultiply(B, Uk, auxBxU);
        Matrix2.matrixAdd(Xk_km1, auxBxU, Xk_km1);

        //Pk|k-1 = Fk*Pk-1|k-1*Fk(t) + Qk
        Matrix2.matrixMultiply(F, Pk_k, auxSDxSD);
        Matrix2.matrixMultiplyByTranspose(auxSDxSD, F, Pk_km1);
        Matrix2.matrixAdd(Pk_km1, Q, Pk_km1);
    }

    public void update() {
        //Yk = Zk - Hk*Xk|k-1
        Matrix2.matrixMultiply(H, Xk_km1, Yk);
        Matrix2.matrixSubtract(Zk, Yk, Yk);

        //Sk = Rk + Hk*Pk|k-1*Hk(t)
        Matrix2.matrixMultiplyByTranspose(Pk_km1, H, auxSDxMD);
        Matrix2.matrixMultiply(H, auxSDxMD, Sk);
        Matrix2.matrixAdd(R, Sk, Sk);

        //Kk = Pk|k-1*Hk(t)*Sk(inv)
        if (!(Matrix2.matrixDestructiveInvert(Sk, SkInv)))
            return; //Matrix2 hasn't inversion
        Matrix2.matrixMultiply(auxSDxMD, SkInv, K);

        //xk|k = xk|k-1 + Kk*Yk
        Matrix2.matrixMultiply(K, Yk, Xk_k);
        Matrix2.matrixAdd(Xk_km1, Xk_k, Xk_k);

        //Pk|k = (I - Kk*Hk) * Pk|k-1 - SEE WIKI!!!
        Matrix2.matrixMultiply(K, H, auxSDxSD);
        Matrix2.matrixSubtractFromIdentity(auxSDxSD);
        Matrix2.matrixMultiply(auxSDxSD, Pk_km1, Pk_k);

        //we don't use this :
        //Yk|k = Zk - Hk*Xk|k
        //Matrix2.matrixMultiply(H, Xk_k, Yk_k);
        //Matrix2.matrixSubtract(Zk, Yk_k, Yk_k);
    }
}
