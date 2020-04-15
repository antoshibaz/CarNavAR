package com.app.carnavar.utils.android;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

public class DisplayMessagesUtils {

    public static void showToastMsg(Context context, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        });
    }

    public static Snackbar buildAndShowSnackbar(Activity context, boolean autoHiding, String msg) {
        Snackbar snackbar = Snackbar.make(context.findViewById(android.R.id.content), msg,
                autoHiding ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_INDEFINITE);
        new Handler(Looper.getMainLooper()).post(snackbar::show);
        return snackbar;
    }

    public static void showSnackbar(Snackbar snackbar, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (msg != null) {
                snackbar.setText(msg);
            }
            snackbar.show();
        });
    }

    public static void hideSnackbar(Snackbar snackbar) {
        new Handler(Looper.getMainLooper()).post(snackbar::dismiss);
    }
}
