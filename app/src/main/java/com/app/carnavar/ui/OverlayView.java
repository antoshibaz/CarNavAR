package com.app.carnavar.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;
import java.util.List;

/**
 * A simple View providing a render callback to other classes.
 */
public class OverlayView extends View {

    private final List<DrawCallback> drawCallbacks = new LinkedList<DrawCallback>();

    /**
     * Interface defining the callback for client classes.
     */
    public interface DrawCallback {
        void drawCallback(final Canvas canvas);
    }

    public void addDrawCallback(final DrawCallback callback) {
        drawCallbacks.add(callback);
    }

    public OverlayView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public synchronized void draw(final Canvas canvas) {
        for (final DrawCallback callback : drawCallbacks) {
            callback.drawCallback(canvas);
        }
    }
}