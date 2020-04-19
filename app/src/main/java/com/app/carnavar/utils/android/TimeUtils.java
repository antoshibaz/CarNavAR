package com.app.carnavar.utils.android;

import android.os.SystemClock;

public class TimeUtils {

    // nanoseconds to seconds
    public static final double NS2S = 1.0f / 1000000000.0f;
    // nanoseconds to milliseconds
    public static final double NS2MS = 1.0f / 1000000.0f;
    // nanoseconds to microseconds
    public static final double NS2MCS = 1.0f / 1000.0f;
    // milliseconds to seconds
    public static final double MS2S = 1.0f / 1000.0f;

    public static double nanos2sec(long nanoseconds) {
        return nanoseconds * NS2S;
    }

    public static double millis2sec(long milliseconds) {
        return milliseconds * MS2S;
    }

    public static long nanos2millis(long nanoseconds) {
        return (long) (nanoseconds * NS2MS);
    }

    public static long nanos2micros(long nanoseconds) {
        return (long) (nanoseconds * NS2MCS);
    }

    public static long currentJavaSystemTimeNanos() {
        return System.nanoTime();
    }

    public static long currentJavaSystemTimeMicros() {
        return nanos2micros(currentJavaSystemTimeNanos());
    }

    public static long currentJavaSystemTimeMillis() {
        return nanos2millis(currentJavaSystemTimeNanos());
    }

    public static long currentJavaSystemTimeSec() {
        return (long) nanos2sec(currentJavaSystemTimeNanos());
    }

    public static long currentAndroidSystemTimeNanos() {
        return SystemClock.elapsedRealtimeNanos();
    }

    public static long currentAndroidSystemTimeMicros() {
        return nanos2micros(currentAndroidSystemTimeNanos());
    }

    public static long currentAndroidSystemTimeMillis() {
        return nanos2millis(currentAndroidSystemTimeNanos());
    }

    public static long currentAndroidSystemTimeSec() {
        return (long) nanos2sec(currentAndroidSystemTimeNanos());
    }

    public static long currentJavaSystemTimestampMillis() {
        return System.currentTimeMillis();
    }
}
