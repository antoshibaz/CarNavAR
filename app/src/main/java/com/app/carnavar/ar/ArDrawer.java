package com.app.carnavar.ar;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;

public class ArDrawer {

    public static void drawOverlayNavigationBeacon(Vector3 poiScreenPoint,
                                                   Canvas canvasFromOverlaySurface,
                                                   double distanceMeters,
                                                   boolean hideIfInFOV) {
        // hide nav beacon if it's marker in view of field
        if (hideIfInFOV && poiScreenPoint.z > 0 && (poiScreenPoint.x > 0 && poiScreenPoint.x < canvasFromOverlaySurface.getWidth())
                && (poiScreenPoint.y > 0 && poiScreenPoint.y < canvasFromOverlaySurface.getHeight())) {
            return;
        }

        Vector3 beacon = coordinateNavigationBeacon(poiScreenPoint, canvasFromOverlaySurface);
        drawNavigationBeacon(beacon, canvasFromOverlaySurface, Math.round(distanceMeters) + "m");
    }

    private static Vector3 coordinateNavigationBeacon(Vector3 poiScreenPoint,
                                                      Canvas canvasFromOverlaySurface) {
        Log.d("coords", poiScreenPoint.toString());
        float clipX = Math.abs(poiScreenPoint.x % canvasFromOverlaySurface.getWidth());
        float clipY = Math.abs(poiScreenPoint.y % canvasFromOverlaySurface.getHeight());
        if (poiScreenPoint.x > 0 && poiScreenPoint.y > 0) { // poi in right or down (device in left-top quadrant)
            if (Math.abs(poiScreenPoint.y) >= canvasFromOverlaySurface.getHeight()) {
                clipY = canvasFromOverlaySurface.getHeight();
            }
            if (Math.abs(poiScreenPoint.x) >= canvasFromOverlaySurface.getWidth()) {
                clipX = canvasFromOverlaySurface.getWidth();
            }

            if (Math.abs(poiScreenPoint.y) >= canvasFromOverlaySurface.getHeight()) {
                clipY = canvasFromOverlaySurface.getHeight();
            }
            if (Math.abs(poiScreenPoint.x) >= canvasFromOverlaySurface.getWidth()) {
                clipX = canvasFromOverlaySurface.getWidth();
            }
        }
        if (poiScreenPoint.x > 0 && poiScreenPoint.y < 0) { // poi in right or up (device in left-bottom quadrant)
            if (Math.abs(poiScreenPoint.y) >= 0) {
                clipY = 0f;
            }
            if (Math.abs(poiScreenPoint.x) >= canvasFromOverlaySurface.getWidth()) {
                clipX = canvasFromOverlaySurface.getWidth();
            }
        }
        if (poiScreenPoint.x < 0 && poiScreenPoint.y > 0) { // poi in left or down (device in right-top quadrant)
            if (Math.abs(poiScreenPoint.y) >= canvasFromOverlaySurface.getHeight()) {
                clipY = canvasFromOverlaySurface.getHeight();
            }
            if (Math.abs(poiScreenPoint.x) >= 0) {
                clipX = 0f;
            }
        }
        if (poiScreenPoint.x < 0 && poiScreenPoint.y < 0) { // poi in left or down (device in right-bottom quadrant)
            clipX = 0;
            if (Math.abs(poiScreenPoint.y) < canvasFromOverlaySurface.getHeight()) {
                clipY = Math.abs(poiScreenPoint.y);
            } else {
                clipY = Math.abs(poiScreenPoint.x);
            }

            if (Math.abs(poiScreenPoint.x) > canvasFromOverlaySurface.getHeight() + canvasFromOverlaySurface.getWidth()) {
                clipY = canvasFromOverlaySurface.getHeight();
            }
        }

        Vector3 beacon = new Vector3();
        beacon.x = clipX;
        beacon.y = clipY;

        Log.d("beacon", beacon.toString());
        if (poiScreenPoint.z <= 0) {
            float newX = beacon.x, newY = beacon.y;
            if (poiScreenPoint.x >= 0 && poiScreenPoint.x < canvasFromOverlaySurface.getWidth() &&
                    poiScreenPoint.y >= 0 && poiScreenPoint.y < canvasFromOverlaySurface.getHeight()) {
                newY = canvasFromOverlaySurface.getHeight() - poiScreenPoint.y;
                if (poiScreenPoint.x <= canvasFromOverlaySurface.getWidth() / 2f) {
                    newX = canvasFromOverlaySurface.getWidth();
                } else {
                    newX = 0;
                }
            } else {
                // TODO: fix dispatch conditions (0,0),(w,0),(0,h),(w,h)
                boolean xSet = false, ySet = false;
                if (beacon.x == 0) {
                    newX = canvasFromOverlaySurface.getWidth();
                    xSet = true;
                } else if (beacon.x == canvasFromOverlaySurface.getWidth()) {
                    newX = 0;
                    xSet = true;
                }
                if (beacon.y == 0) {
                    newY = canvasFromOverlaySurface.getHeight();
                    ySet = true;
                } else if (beacon.y == canvasFromOverlaySurface.getHeight()) {
                    newY = 0;
                    ySet = true;
                }

                if (!ySet) {
                    newY = canvasFromOverlaySurface.getHeight() - newY;
                }
                if (!xSet) {
                    newX = canvasFromOverlaySurface.getWidth() - newX;
                }
            }

            beacon.x = newX;
            beacon.y = newY;
        }

        return beacon;
    }

    private static void drawNavigationBeacon(Vector3 beacon, Canvas canvasFromOverlaySurface,
                                             String text) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(36);

        Path path = new Path();
        path.moveTo(160.0f, 240.0f);
        path.lineTo(140.0f, 200.0f);
        path.addArc(new RectF(140, 180, 180, 220), -180, 180);
        path.lineTo(160.0f, 240.0f);
        path.close();
        Matrix matrix = new Matrix();
        RectF bounds = new RectF();
        matrix.setScale(1.7f, 1.7f);
        path.transform(matrix);
        matrix.reset();
        path.computeBounds(bounds, true);
        //postrotate
        double xb = canvasFromOverlaySurface.getWidth() / 2f;
        double yb = canvasFromOverlaySurface.getHeight() / 2f;
        double x = beacon.x - xb;
        double y = yb - beacon.y;
        double theta = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
        theta = (theta + 90 + 360) % 360;
        matrix.postRotate((float) (360f - theta), bounds.centerX(), bounds.centerY());
        path.transform(matrix);
        matrix.reset();
        path.computeBounds(bounds, true);
        float trX = (beacon.x == 0) ? -bounds.left + beacon.x : -bounds.right + beacon.x;
        float trY = (beacon.y == 0) ? -bounds.top + beacon.y : -bounds.bottom + beacon.y;
        matrix.setTranslate(trX, trY);
        path.transform(matrix);
        path.computeBounds(bounds, true);
        trX = (beacon.x == 0) ? -bounds.left + beacon.x : -bounds.right + beacon.x;
        trY = (beacon.y == 0) ? -bounds.top + beacon.y : -bounds.bottom + beacon.y;
        canvasFromOverlaySurface.drawPath(path, paint);
        Rect textBounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), textBounds);
        if (beacon.x == 0) {
            trX = beacon.x + bounds.width();
        } else if (beacon.x == canvasFromOverlaySurface.getWidth()) {
            trX = beacon.x - bounds.width() - paint.measureText(text);
        } else {
            trX = beacon.x - bounds.width();
        }
        if (beacon.y == 0) {
            trY = beacon.y + bounds.height() + textBounds.height();
        } else if (beacon.y == canvasFromOverlaySurface.getHeight()) {
            trY = beacon.y - bounds.height() - textBounds.height();
        } else {
            trY = beacon.y - bounds.height() / 2;
        }
        canvasFromOverlaySurface.drawText(text, trX, trY, paint);
    }

    public static Node getBaseModelRenderableNode(Renderable renderable) {
        Node base = new Node();
        base.setRenderable(renderable);
        return base;
    }

    public static Node getBaseViewRenderableNode(ViewRenderable renderable) {
        Node base = new Node();
        base.setRenderable(renderable);
        return base;
    }
}
