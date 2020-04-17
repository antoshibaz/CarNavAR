package com.app.carnavar.ar.arcorelocation;

import android.app.Activity;
import android.location.Location;
import android.os.Handler;
import android.util.Log;

import com.app.carnavar.ar.arcorelocation.rendering.LocationNode;
import com.app.carnavar.utils.maps.MapsUtils;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.math.Vector3;

import java.util.ArrayList;

public class LocationScene {

    public static final String TAG = LocationScene.class.getSimpleName();

    private float RENDER_DISTANCE = 25f;
    public ArSceneView mArSceneView;
    //public DeviceLocation deviceLocation;
    //public DeviceOrientation deviceOrientation;
    public Activity context;
    public ArrayList<LocationMarker> mLocationMarkers = new ArrayList<>();
    // Anchors are currently re-drawn on an interval. There are likely better
    // ways of doing this, however it's sufficient for now.
    private int anchorRefreshInterval = 1000 * 5; // 5 seconds
    // Limit of where to draw markers within AR scene.
    // They will auto scale, but this helps prevents rendering issues
    private int distanceLimit = 30;
    private boolean offsetOverlapping = false;
    private boolean removeOverlapping = false;
    private boolean anchorsNeedRefresh = true;
    private boolean minimalRefreshing = false;
    private boolean refreshAnchorsAsLocationChanges = false;
    private Handler mHandler = new Handler();
    Runnable anchorRefreshTask = new Runnable() {
        @Override
        public void run() {
            anchorsNeedRefresh = true;
            mHandler.postDelayed(anchorRefreshTask, anchorRefreshInterval);
        }
    };
    private boolean debugEnabled = false;
    private Session mSession;

    private Location currentLocation = null;
    private double currentBearing = 0.0f;
    // Bearing adjustment. Can be set to calibrate with true north
    private int bearingAdjustment = 0;

    public LocationScene(Activity context, ArSceneView mArSceneView) {
        this.context = context;
        this.mSession = mArSceneView.getSession();
        this.mArSceneView = mArSceneView;

        startCalculationTask();

//        deviceLocation = new DeviceLocation(context, this);
//        deviceOrientation = new DeviceOrientation(context);
//        deviceOrientation.resume();
        //test();
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public double getCurrentBearing() {
        return currentBearing;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public boolean minimalRefreshing() {
        return minimalRefreshing;
    }

    public void setMinimalRefreshing(boolean minimalRefreshing) {
        this.minimalRefreshing = minimalRefreshing;
    }

    public boolean refreshAnchorsAsLocationChanges() {
        return refreshAnchorsAsLocationChanges;
    }

    public void setRefreshAnchorsAsLocationChanges(boolean refreshAnchorsAsLocationChanges) {
        if (refreshAnchorsAsLocationChanges) {
            stopCalculationTask();
        } else {
            startCalculationTask();
        }
        refreshAnchors();
        this.refreshAnchorsAsLocationChanges = refreshAnchorsAsLocationChanges;
    }

    public int getAnchorRefreshInterval() {
        return anchorRefreshInterval;
    }

    /**
     * Set the interval at which anchors should be automatically re-calculated.
     *
     * @param anchorRefreshInterval
     */
    public void setAnchorRefreshInterval(int anchorRefreshInterval) {
        this.anchorRefreshInterval = anchorRefreshInterval;
        stopCalculationTask();
        startCalculationTask();
    }

    public void clearMarkers() {
        for (LocationMarker lm : mLocationMarkers) {
            if (lm.anchorNode != null) {
                lm.anchorNode.getAnchor().detach();
                lm.anchorNode.setEnabled(false);
                lm.anchorNode = null;
            }

        }
        mLocationMarkers = new ArrayList<>();
    }

    /**
     * The distance cap for distant markers.
     * ARCore doesn't like markers that are 2000km away :/
     *
     * @return
     */
    public int getDistanceLimit() {
        return distanceLimit;
    }

    /**
     * The distance cap for distant markers.
     * Render distance limit is 30 meters, impossible to change that for now
     * https://github.com/google-ar/sceneform-android-sdk/issues/498
     */
    public void setDistanceLimit(int distanceLimit) {
        this.distanceLimit = distanceLimit;
    }

    public boolean shouldOffsetOverlapping() {
        return offsetOverlapping;
    }

    public boolean shouldRemoveOverlapping() {
        return removeOverlapping;
    }

    /**
     * Attempts to raise markers vertically when they overlap.
     * Needs work!
     *
     * @param offsetOverlapping
     */
    public void setOffsetOverlapping(boolean offsetOverlapping) {
        this.offsetOverlapping = offsetOverlapping;
    }


    /**
     * Remove farthest markers when they overlap
     *
     * @param removeOverlapping
     */
    public void setRemoveOverlapping(boolean removeOverlapping) {
        this.removeOverlapping = removeOverlapping;

//        for (LocationMarker mLocationMarker : mLocationMarkers) {
//            LocationNode anchorNode = mLocationMarker.anchorNode;
//            if (anchorNode != null) {
//                anchorNode.setEnabled(true);
//            }
//        }
    }

    public void processFrame(Frame frame) {
        refreshAnchorsIfRequired(frame);
    }

    /**
     * Force anchors to be re-calculated
     */
    public void refreshAnchors() {
        anchorsNeedRefresh = true;
    }

    private void refreshAnchorsIfRequired(Frame frame) {
        if (!anchorsNeedRefresh) {
            return;
        }

        anchorsNeedRefresh = false;
        Log.i(TAG, "Refreshing anchors...");

        if (currentLocation == null) {
            Log.i(TAG, "Location not yet established.");
            return;
        }

        for (int i = 0; i < mLocationMarkers.size(); i++) {
            try {
                final LocationMarker marker = mLocationMarkers.get(i);
                int markerDistance = (int) Math.round(
                        MapsUtils.haversineDistance3d(
                                marker.latitude,
                                marker.longitude,
                                0,
                                currentLocation.getLatitude(),
                                currentLocation.getLongitude(),
                                0)
                );

                if (markerDistance > marker.getOnlyRenderWhenWithin()) {
                    // Don't render if this has been set and we are too far away.
                    Log.i(TAG, "Not rendering. Marker distance: " + markerDistance
                            + " Max render distance: " + marker.getOnlyRenderWhenWithin());
                    continue;
                }

                float bearing = (float) MapsUtils.calcBearing(
                        currentLocation.getLatitude(),
                        currentLocation.getLongitude(),
                        marker.latitude,
                        marker.longitude);

                float markerBearing = bearing - (float) currentBearing;

                // Bearing adjustment can be set if you are trying to
                // correct the heading of north - setBearingAdjustment(10)
                markerBearing = markerBearing + bearingAdjustment + 360;
                markerBearing = markerBearing % 360;

                double rotation = Math.floor(markerBearing);

                Log.d(TAG, "currentDeviceBearing:" + (float) currentBearing
                        + " bearingMyLocationToMarker:" + bearing + " markerBearing:" + markerBearing
                        + " rotation:" + rotation + " distanceMuLocationToMarker:" + markerDistance);

                // When pointing device upwards (camera towards sky)
                // the compass bearing can flip.
                // In experiments this seems to happen at pitch~=-25
                //if (deviceOrientation.pitch > -25)
                //rotation = rotation * Math.PI / 180;

                int renderDistance = markerDistance;

                // Limit the distance of the Anchor within the scene.
                // Prevents rendering issues.
                if (renderDistance > distanceLimit)
                    renderDistance = distanceLimit;

                // Adjustment to add markers on horizon, instead of just directly in front of camera
                double heightAdjustment = 0;
                // Math.round(renderDistance * (Math.tan(Math.toRadians(deviceOrientation.pitch)))) - 1.5F;

                // Raise distant markers for better illusion of distance
                // Hacky - but it works as a temporary measure
                int cappedRealDistance = markerDistance > 500 ? 500 : markerDistance;
                if (renderDistance != markerDistance)
                    heightAdjustment += 0.005F * (cappedRealDistance - renderDistance);

                float z = -Math.min(renderDistance, RENDER_DISTANCE);

                double rotationRadian = Math.toRadians(rotation);

                float zRotated = (float) (z * Math.cos(rotationRadian));
                float xRotated = (float) -(z * Math.sin(rotationRadian));

                float y = frame.getCamera().getDisplayOrientedPose().ty() + (float) heightAdjustment;

                if (marker.anchorNode != null && marker.anchorNode.getAnchor() != null) {
                    marker.anchorNode.getAnchor().detach();
                    marker.anchorNode.setAnchor(null);
                    marker.anchorNode.setEnabled(false);
                    marker.anchorNode = null;
                }

                // Don't immediately assign newly created anchor in-case of exceptions
                Pose translation = Pose.makeTranslation(xRotated, y, zRotated);
                Anchor newAnchor = mSession.createAnchor(
                        frame.getCamera()
                                .getDisplayOrientedPose()
                                .compose(translation)
                                .extractTranslation()
                );

                marker.anchorNode = new LocationNode(newAnchor, marker, this);
                marker.anchorNode.setScalingMode(LocationMarker.ScalingMode.NO_SCALING);

                marker.anchorNode.setParent(mArSceneView.getScene());
                marker.anchorNode.addChild(mLocationMarkers.get(i).node);
                marker.node.setLocalPosition(Vector3.zero());

                if (marker.getRenderEvent() != null) {
                    marker.anchorNode.setRenderEvent(marker.getRenderEvent());
                }

                marker.anchorNode.setScaleModifier(marker.getScaleModifier());
                marker.anchorNode.setScalingMode(marker.getScalingMode());
                marker.anchorNode.setGradualScalingMaxScale(marker.getGradualScalingMaxScale());
                marker.anchorNode.setGradualScalingMinScale(marker.getGradualScalingMinScale());

                // Locations further than RENDER_DISTANCE are remapped to be rendered closer.
                // => height differential also has to ensure the remap is correct
                if (markerDistance > RENDER_DISTANCE) {
                    float renderHeight = RENDER_DISTANCE * marker.getHeight() / markerDistance;
                    marker.anchorNode.setHeight(renderHeight);
                } else {
                    marker.anchorNode.setHeight(marker.getHeight());
                }

                if (minimalRefreshing)
                    marker.anchorNode.scaleAndRotate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //this is bad, you should feel bad
        System.gc();
    }

    public void updateGpsLocation(Location location) {
        currentLocation = location;
        if (refreshAnchorsAsLocationChanges()) {
            refreshAnchors();
        }
    }

    public void updateBearing(double bearing) {
        currentBearing = bearing;
    }

    /**
     * Adjustment for compass bearing.
     *
     * @return
     */
    public int getBearingAdjustment() {
        return bearingAdjustment;
    }

    /**
     * Adjustment for compass bearing.
     * You may use this for a custom method of improving precision.
     *
     * @param i
     */
    public void setBearingAdjustment(int i) {
        bearingAdjustment = i;
        anchorsNeedRefresh = true;
    }

    /**
     * Resume sensor services. Important!
     */
    public void onPause() {
//        deviceOrientation.resume();
//        deviceLocation.resume();
    }

    /**
     * Pause sensor services. Important!
     */
    public void onResume() {
//        deviceOrientation.pause();
//        deviceLocation.pause();
    }

    void startCalculationTask() {
        anchorRefreshTask.run();
    }

    void stopCalculationTask() {
        mHandler.removeCallbacks(anchorRefreshTask);
    }
}
