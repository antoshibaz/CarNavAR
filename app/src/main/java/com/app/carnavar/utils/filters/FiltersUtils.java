package com.app.carnavar.utils.filters;

public class FiltersUtils {

    // approach for compute dynamic alpha (gives stability smoothing on devices with required timeConstant)
    // alpha = dt / (timeConstant + dt)
    // dt = 1 / (countSampling / ((timestampLastSample - timestampInit) / 1000000000.0f))
    // dt - sec, timestamp - nanosec

    // low-pass filter, or exponentially weighted moving average, alpha=0.0..1.0
    public static float[] lowPassFilteringArray(float[] newValuesVec, float[] prevValuesVec, float alpha) {
        for (int i = 0; i < newValuesVec.length; i++) {
            newValuesVec[i] = lowPassFiltering(newValuesVec[i], prevValuesVec[i], alpha);
        }
        return newValuesVec;
    }

    public static float lowPassFiltering(float newValue, float prevValue, float alpha) {
        return alpha * newValue + (1 - alpha) * prevValue; // = prevValue + alpha * (newValue - prevValue)
    }
}
