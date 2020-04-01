package com.app.carnavar;

import android.annotation.SuppressLint;
import android.app.Application;
import android.util.Log;

import com.google.android.filament.BuildConfig;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import timber.log.Timber;

@SuppressLint("Registered")
public class App extends Application {

    public static final String TAG = App.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        initApp();
    }

    private BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV loaded successfully");
                    // Load ndk-built or cmake module, as specified in moduleName in build.gradle
                    // after opencv initialization
                    break;
                default:
                    super.onManagerConnected(status);
            }
        }
    };

    private void initApp() {
        // init Timber logger
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        // init opencv lib
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, baseLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        // init other native libs
        System.loadLibrary("native-lib");

        // maybe init ARCore?
    }
}
