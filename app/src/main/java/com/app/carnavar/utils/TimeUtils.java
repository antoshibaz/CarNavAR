package com.app.carnavar.utils;

import android.os.SystemClock;

public class TimeUtils {

    // nanoseconds to seconds
    public static final double NS2S = 1.0f / 1000000000.0f;
    // nanoseconds to milliseconds
    public static final double NS2MS = 1.0f / 1000000.0f;
    // nanoseconds to microseconds
    public static final double NS2MCS = 1.0f / 1000.0f;

    public static long nano2sec(long nanoseconds) {
        return (long) (nanoseconds * NS2S);
    }

    public static long nano2milli(long nanoseconds) {
        return (long) (nanoseconds * NS2MS);
    }

    public static long nano2micro(long nanoseconds) {
        return (long) (nanoseconds * NS2MCS);
    }

    public static long currentJavaSystemTimeNanos() {
        return System.nanoTime();
    }

    public static long currentJavaSystemTimeMicros() {
        return nano2micro(currentJavaSystemTimeNanos());
    }

    public static long currentJavaSystemTimeMillis() {
        return nano2milli(currentJavaSystemTimeNanos());
    }

    public static long currentJavaSystemTimeSec() {
        return nano2sec(currentJavaSystemTimeNanos());
    }

    public static long currentAndroidSystemTimeNanos() {
        return SystemClock.elapsedRealtimeNanos();
    }

    public static long currentAndroidSystemTimeMicros() {
        return nano2micro(currentAndroidSystemTimeNanos());
    }

    public static long currentAndroidSystemTimeMillis() {
        return nano2milli(currentAndroidSystemTimeNanos());
    }

    public static long currentAndroidSystemTimeSec() {
        return nano2sec(currentAndroidSystemTimeNanos());
    }

    public static long currentJavaSystemTimestampMillis() {
        return System.currentTimeMillis();
    }
}
