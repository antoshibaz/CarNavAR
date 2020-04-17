package com.app.carnavar.utils.filters;

public class SmoothingFilters {

    public static class LowPassFilter {

        private float prevValue;
        private float[] prevValues;

        public float process(float value, float alpha) {
            prevValue = FiltersUtils.lowPassFiltering(value, prevValue, alpha);
            return prevValue;
        }

        public float[] processArray(float[] values, float alpha) {
            if (prevValues == null || values.length != prevValues.length) {
                prevValues = new float[values.length];
            }
            prevValues = FiltersUtils.lowPassFilteringArray(values, prevValues, alpha);
            return prevValues;
        }
    }

    public static class HighPassFilter {

    }

    public static class MedianFilter {

    }

    public static class GaussianFilter {

    }
}
