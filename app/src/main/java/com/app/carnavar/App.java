package com.app.carnavar;

import android.annotation.SuppressLint;
import android.app.Application;

import com.google.android.filament.BuildConfig;

import timber.log.Timber;

@SuppressLint("Registered")
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // init Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }
}
