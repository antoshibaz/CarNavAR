package com.app.carnavar.utils.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import com.app.carnavar.R;
import com.mapbox.mapboxsdk.utils.BitmapUtils;

import androidx.core.content.ContextCompat;

public class ThemeManager {

    /**
     * Returns a map marker {@link Bitmap} based on the current theme setting.
     *
     * @param context to retrieve the drawable for the given resource ID
     * @return {@link Bitmap} map marker dark or light
     */
    public static Bitmap retrieveThemeMapMarker(Context context) {
        TypedValue destinationMarkerResId = resolveAttributeFromId(context, R.attr.navigationViewDestinationMarker);
        int markerResId = destinationMarkerResId.resourceId;
        Drawable markerDrawable = ContextCompat.getDrawable(context, markerResId);
        return BitmapUtils.getBitmapFromDrawable(markerDrawable);
    }

    private static TypedValue resolveAttributeFromId(Context context, int resId) {
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(resId, outValue, true);
        return outValue;
    }
}
