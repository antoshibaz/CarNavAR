package com.app.carnavar.utils.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Toast;

public class DisplayMessagesUtils {

    public static void showToastMsg(Context context, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        });
    }

    public static void showSnackbarMsg(Context context, String msg) {

    }
}
