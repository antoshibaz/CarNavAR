package com.app.carnavar;

import android.content.Context;
import android.content.Intent;
import android.hardware.GeomagneticField;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.app.carnavar.ar.arcorelocation.LocationMarker;
import com.app.carnavar.ar.arcorelocation.LocationScene;
import com.app.carnavar.hal.sensors.SensorTypes;
import com.app.carnavar.maps.NavMap;
import com.app.carnavar.services.ServicesRepository;
import com.app.carnavar.services.gpsimu.GpsImuService;
import com.app.carnavar.services.gpsimu.GpsImuServiceInterfaces;
import com.app.carnavar.utils.android.DisplayMessagesUtils;
import com.app.carnavar.utils.android.DisplayUtils;
import com.app.carnavar.utils.android.LibsUtils;
import com.app.carnavar.utils.filters.LocationFilters;
import com.app.carnavar.utils.filters.SmoothingFilters;
import com.app.carnavar.utils.maps.MapsUtils;
import com.app.carnavar.utils.math.Matrix2;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.maps.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ArActivity extends AppCompatActivity {

    public static final String TAG = "ArActivity";

    private boolean hasRenderersFinishedLoading = false;
    private boolean navMapInitSuccess = false;

    private Session arCoreSession;
    private ArSceneView arSceneView;

    // ARCore-Location scene world
    private LocationScene locationScene;

    // Renderables objects (views, models and other)
    private ViewRenderable poiViewRenderable;
    private Renderable arrowRenderable;

    private MapView navMapView;
    private NavMap navMap;

    private SharedCamera sharedCamera;
    private ImageReader imageReader;

    private LocationMarker targetDestinationMarker;

    private LocationFilters.GeoHeuristicFilter geoHeuristicFilter = new LocationFilters.GeoHeuristicFilter();

    private SmoothingFilters.LowPassFilter poseFilter;
    private static final double POSE_FILTERING_FACTOR = 0.1;

    private float[] currentPoseAngles;

    GeomagneticField geomagneticField;
    double sx;
    double bx;
    double sy;
    double by;
    boolean isCalib = false;

    private GpsImuServiceInterfaces.ImuListener imuListener = (values, sensorType, timeNanos) -> {
        if (sensorType == SensorTypes.ORIENTATION_ROTATION_ANGLES) {
            currentPoseAngles = poseFilter.processArray(values, (float) POSE_FILTERING_FACTOR);
            if (navMap != null && navMapInitSuccess) {
                navMap.updateOrientation(currentPoseAngles[0]);
            }
            if (locationScene != null) {
                locationScene.updateBearing(currentPoseAngles[0]);
            }
            Log.d(TAG, " ar_bearing=" + currentPoseAngles[0]);
        } else if (sensorType == SensorTypes.MAGNETIC_FIELD) {
            collectCalib1(values);

            if (geomagneticField != null && sx != 0 && sy != 0) {
                double calibMx = (values[0] + bx) / sx;
                double calibMy = (values[1] + by) / sy;
                double offset = 0;
                if (calibMx < 0) {
                    offset = -180;
                }

                double calibBearing = ((Math.toDegrees(Math.atan2(calibMy, calibMx)))
                        + geomagneticField.getDeclination() + offset + 360f) % 360f;
                Log.d(TAG, "with_calib_bearing=" + calibBearing);
            }
        }
    };

    Location lastLocation;
    boolean isrun = false;
    private GpsImuServiceInterfaces.GpsLocationListener gpsLocationListener = location -> {
        Location filteredLocation = geoHeuristicFilter.process(location);
        Log.d(TAG, "Filtered location -> bearIsEstablished=" + geoHeuristicFilter.bearingIsEstablished()
                + " " + MapsUtils.toString(filteredLocation));
        lastLocation = filteredLocation;

        if (navMap != null && navMapInitSuccess) {
            navMap.updateLocation(filteredLocation);
            if (navMap.getCurrentDestinationPoint() == null) {
                // get data from previous activity
                Intent intent = getIntent();
                final double[] dstPoiMarker = Objects.requireNonNull(intent.getExtras()).getDoubleArray("destination_marker");
                if (dstPoiMarker != null) {
                    navMap.replaceMarker(Point.fromLngLat(dstPoiMarker[0], dstPoiMarker[1]));
                }
            }
        }
        if (locationScene != null) {
            locationScene.updateGpsLocation(filteredLocation);
        }

        if (!isrun) {
            isrun = true;
            testCalib(1000);
        }
    };

    Matrix2 x1Res = new Matrix2(2, 1);
    Matrix2 x2Res = new Matrix2(2, 1);

    private void calib(Double[] hxArr, Double[] hyArr, double hh, Double[] extBearings) {
        if (hxArr.length != extBearings.length
                || hyArr.length != extBearings.length) {
            return;
        }

        Matrix2 h1 = new Matrix2(hxArr.length, 2);
        Matrix2 h2 = new Matrix2(hyArr.length, 2);
        double kh1 = 1 / hh;
        double kh2 = -1 / hh;
        for (int i = 0; i < hxArr.length; i++) {
            h1.data[i][0] = hxArr[i] * kh1;
            h1.data[i][1] = -1 * kh1;
            h2.data[i][0] = hyArr[i] * kh2;
            h2.data[i][1] = -1 * kh2;
        }

        Matrix2 y1 = new Matrix2(extBearings.length, 1);
        Matrix2 y2 = new Matrix2(extBearings.length, 1);
        for (int i = 0; i < extBearings.length; i++) {
            y1.data[i][0] = Math.cos(Math.toRadians(extBearings[i]));
            y2.data[i][0] = Math.sin(Math.toRadians(extBearings[i]));
        }

        Matrix2 h1T = new Matrix2(2, hxArr.length);
        Matrix2 h2T = new Matrix2(2, hyArr.length);
        Matrix2.matrixTranspose(h1, h1T);
        Matrix2.matrixTranspose(h2, h2T);

        Matrix2 mul1 = new Matrix2(2, 2);
        Matrix2 mul2 = new Matrix2(2, 2);
        Matrix2 mul1inv = new Matrix2(2, 2);
        Matrix2 mul2inv = new Matrix2(2, 2);
        Matrix2.matrixMultiply(h1T, h1, mul1);
        Matrix2.matrixMultiply(h2T, h2, mul2);
        if (!Matrix2.matrixDestructiveInvert(mul1, mul1inv)) {
            Log.d(TAG, "mat not invert 1");
            return;
        }
        if (!Matrix2.matrixDestructiveInvert(mul2, mul2inv)) {
            Log.d(TAG, "mat not invert 2");
            return;
        }

        Matrix2 y1Mul = new Matrix2(2, hxArr.length);
        Matrix2 y2Mul = new Matrix2(2, hyArr.length);
        Matrix2.matrixMultiply(mul1inv, h1T, y1Mul);
        Matrix2.matrixMultiply(mul2inv, h2T, y2Mul);
        Matrix2.matrixMultiply(y1Mul, y1, x1Res);
        Matrix2.matrixMultiply(y2Mul, y2, x2Res);
    }

    private void testCalib(long time) {
        new Handler().postDelayed(() -> {
            if (extBearings.size() >= 5) {
                calib(hxArr.toArray(new Double[0]), hyArr.toArray(new Double[0]), hh, extBearings.toArray(new Double[0]));
                sx = x1Res.data[0][0];
                bx = x1Res.data[1][0];
                sy = x2Res.data[0][0];
                by = x2Res.data[1][0];
                double calibMx = (magVals[0] + bx) / sx;
                double calibMy = (magVals[1] + by) / sy;
                double offset = 0;
                if (calibMx < 0) {
                    offset = -180;
                }

                double calibBearing = ((Math.toDegrees(Math.atan2(calibMy, calibMx)))
                        + geomagneticField.getDeclination() + offset + 360f) % 360f;
                Log.d(TAG, "calib_bearing=" + calibBearing + " calibMx=" + calibMx + " calibMy=" + calibMy
                + " sx=" + sx + " sy=" +  sy);
                extBearings.clear();
                hxArr.clear();
                hyArr.clear();
                testCalib(5000);
            } else {
                collectCalib2(lastLocation);
                testCalib(1000);
            }
        }, time);
    }

    private void collectCalib1(float[] magnetValues) {
        float hdx = magnetValues[0], hdy = magnetValues[1], hdz = magnetValues[2];
        magVals = magFilter.processArray(new float[]{hdx, hdy, hdz}, 0.1f);
//        switch (windowManager.getDefaultDisplay().getRotation()) {
//            case Surface.ROTATION_90:
//            case Surface.ROTATION_270:
//                hdx = magnetValues[1];
//                hdy = magnetValues[2];
//                hdz = magnetValues[0];
//                break;
//            case Surface.ROTATION_180:
//            case Surface.ROTATION_0:
//            default:
//                hdx = magnetValues[0];
//                hdy = magnetValues[2];
//                hdz = magnetValues[1];
//                break;
//        }
    }

    private void collectCalib2(Location location) {
        geomagneticField = new GeomagneticField(
                (float) location.getLatitude(),
                (float) location.getLongitude(),
                (float) location.getAltitude(),
                location.getTime());
        double bearingPhiExt = ((Math.random() < 0.5) ? (358 + Math.random() * 1.9) : Math.random() * 2);
        Log.d(TAG, "pseudo real bear=" + bearingPhiExt);
        bearingPhiExt = ((bearingPhiExt - geomagneticField.getDeclination()) + 360f) % 360f;
        if (hh == 0) {
            hh = geomagneticField.getHorizontalStrength() / 1000;
        }
        extBearings.add(bearingPhiExt);
        hxArr.add((double) magVals[0]);
        hyArr.add((double) magVals[1]);
        Log.d(TAG, "added to calib -> bear=" + bearingPhiExt
                + " hx=" + hxArr.get(hxArr.size() - 1)
                + " hy=" + hyArr.get(hyArr.size() - 1)
                + " hz=" + magVals[2]
                + " hh=" + hh + " total_mag=" + geomagneticField.getFieldStrength() / 1000);
    }

    List<Double> extBearings = new ArrayList<>();
    List<Double> hxArr = new ArrayList<>();
    List<Double> hyArr = new ArrayList<>();
    double hh = 0;
    float[] magVals = new float[3];
    SmoothingFilters.LowPassFilter magFilter = new SmoothingFilters.LowPassFilter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // check arcore availability
        try {
            LibsUtils.ARCore.checkAvailabilityAndInstallIfNeeded(this);
        } catch (UnavailableException e) {
            String excMsg = LibsUtils.ARCore.handleException(e);
            Log.e(TAG, excMsg);
            DisplayMessagesUtils.showToastMsg(this, excMsg);
            finish();
        }

        setContentView(R.layout.activity_ar);
        poseFilter = new SmoothingFilters.LowPassFilter();

        // create arcore scene view and custom session
        arSceneView = findViewById(R.id.ar_scene_view);
        try {
            arCoreSession = LibsUtils.ARCore.createARCoreSession(this, true);
            sharedCamera = arCoreSession.getSharedCamera();
            Log.d(TAG, "Selected ARCore camera -> " + LibsUtils.ARCore.handleCameraConfig(arCoreSession.getCameraConfig()));
            // TODO: shared camera image capturing
            // When ARCore is running, make sure it also updates our CPU image surface.
            //    sharedCamera.setAppSurfaces(this.cameraId, Arrays.asList(cpuImageReader.getSurface()));
            // Use the currently configured CPU image size.
//            Size desiredImageSize = arCoreSession.getCameraConfig().getImageSize();
//            imageReader = ImageReader.newInstance(
//                            desiredImageSize.getWidth(),
//                            desiredImageSize.getHeight(),
//                            ImageFormat.YUV_420_888,
//                            2);
//            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
//                @Override
//                public void onImageAvailable(ImageReader reader) {
//                    Image image = imageReader.acquireLatestImage();
//                    image.close();
//                    Log.d(TAG, "Camera image captured");
//                }
//            }, null);
//            sharedCamera.setAppSurfaces(arCoreSession.getCameraConfig().getCameraId(),
//                    Arrays.asList(imageReader.getSurface()));
        } catch (UnavailableException e) {
            String excMsg = LibsUtils.ARCore.handleException(e);
            Log.e(TAG, excMsg);
            DisplayMessagesUtils.showToastMsg(this, excMsg);
            finish();
        }
        arSceneView.setupSession(arCoreSession);

        // init maps
        navMapView = findViewById(R.id.miniNavMapView);
        navMapView.onCreate(savedInstanceState);
        navMap = new NavMap(navMapView, getString(R.string.mapbox_map_style_streets));
        navMap.setNavMapInitializedListener(() -> {
            navMapInitSuccess = true;
            Log.d(TAG, "NavMap is success loaded");
        });
        navMap.setDestinationMarkerChangedListener((newDstPoint, pointFeatures) -> {
            navMap.updateRoutesFromMyLocationTo(newDstPoint);
            if (hasRenderersFinishedLoading) {
                LocationMarker newDestinationMarker = createDestinationPoiMarker(newDstPoint.latitude(),
                        newDstPoint.longitude());
                if (locationScene != null) {
                    // call to render ar now
                    detachLocationMarkerFromScene(targetDestinationMarker);
                    targetDestinationMarker = newDestinationMarker;
                    attachLocationMarkerToScene(targetDestinationMarker);
                } else {
                    // put to queue for rendering
                    targetDestinationMarker = newDestinationMarker;
                }
            }
        });
        navMap.setUpdateRoutesListener((currentTargetRoute, currentAvailableRoutes) -> navMap.trackingMyLocation(250));

        // Build a renderable from a 2D View
        CompletableFuture<ViewRenderable> poiLayout =
                ViewRenderable.builder()
                        .setView(this, R.layout.poi_ar_view)
                        .build();
        CompletableFuture<ModelRenderable> arrowModel = ModelRenderable.builder()
                .setSource(this, Uri.parse("models/RLab-arrow.sfb"))
                .build();

        CompletableFuture.allOf(
                poiLayout, arrowModel)
                .handle(
                        (notUsed, throwable) -> {
                            // When you build a Renderable, Sceneform loads its resources in the background while
                            // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                            // before calling get()
                            if (throwable != null) {
                                displayError(this, "Unable to load renderables", throwable);
                                return null;
                            }

                            try {
                                poiViewRenderable = poiLayout.get();
                                arrowRenderable = arrowModel.get();
                                hasRenderersFinishedLoading = true;
                            } catch (InterruptedException | ExecutionException ex) {
                                displayError(this, "Unable to load renderables", ex);
                            }

                            return null;
                        });

        arSceneView.getPlaneRenderer().setEnabled(false); // disable plane renderer as it not use in this scenario
        // Set an update listener on the Scene
        arSceneView
                .getScene()
                .addOnUpdateListener(
                        frameTime -> {
                            if (!hasRenderersFinishedLoading) {
                                return;
                            }

                            if (locationScene == null) {
                                // If our locationScene object hasn't been setup yet, this is a good time to do it
                                // We know that here, the AR components have been initiated
                                locationScene = new LocationScene(this, arSceneView);
                                // speed 150 km/h = 42 m/s | refresh - 10-50ms
                                locationScene.refreshAnchors();
                                //locationScene.setAnchorRefreshInterval(30);
                                locationScene.setRefreshAnchorsAsLocationChanges(true);

                                // Now lets create our location markers
                                if (targetDestinationMarker != null
                                        && !locationScene.mLocationMarkers.contains(targetDestinationMarker)) {
                                    attachLocationMarkerToScene(targetDestinationMarker);
                                }

//                                double[] latLng = MapsUtils.getDstLocationByBearingAndDistance(dstPoiMarker[1], dstPoiMarker[0], 180, 100);
//                                LocationMarker arrowNode = new LocationMarker(latLng[1], latLng[0], getArrowModelNode());
//                                arrowNode.setRenderEvent(new LocationNodeRender() {
//                                    @Override
//                                    public void render(LocationNode node) {
////                                        for (Node n : node.getChildren()) {
////                                            n.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 90f));
////                                        }
//                                    }
//                                });
//                                locationScene.mLocationMarkers.add(arrowNode);
                            }

                            Frame frame = arSceneView.getArFrame();
                            if (frame == null) {
                                return;
                            }

                            // image analysis
                            try (Image image = frame.acquireCameraImage()) {
//                                Log.d(TAG, "" + image.getHeight() + "x" + image.getWidth());
                            } catch (NotYetAvailableException e) {
                            }

//                            Pose pose1 = arSceneView.getArFrame().getAndroidSensorPose();
////                            Pose pose2 = arSceneView.getArFrame().getCamera().getPose();
////                            Pose pose3 = arSceneView.getArFrame().getCamera().getDisplayOrientedPose();
//                            float[] rotVec1 = pose1.getRotationQuaternion();
//                            float[] rotMat1 = new float[9]; // or pose1.toMatrix(...)
//                            SensorManager.getRotationMatrixFromVector(rotMat1, rotVec1);
//                            float[] angles1 = new float[3];
//                            SensorManager.getOrientation(rotMat1, angles1);
//                            angles1[0] = ((float) Math.toDegrees(angles1[0]) + 360f) % 360f;
//                            angles1[1] = (float) Math.toDegrees(angles1[1]);
//                            angles1[2] = (float) Math.toDegrees(angles1[2]);
//                            if (loadingMessageSnackbar != null) {
//                                loadingMessageSnackbar.setText("1:" + Math.round(angles1[0]) + " 2:" + Math.round(angles1[1]) + " 3:" + Math.round(angles1[2]));
//                            }
//                            arSceneView.getScene().getCamera().getWorldPosition()


                            if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                                return;
                            }

                            if (locationScene != null) {
                                locationScene.processFrame(frame);
                            }

                            // planes detecting
//                            for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
//                                if (plane.getTrackingState() == TrackingState.TRACKING) {
//                                    hideLoadingMessage();
//                                }
//                            }
                        });
    }

    private static LocationMarker getBaseLocationMarker(double lat, double lng, Node renderableNode) {
        return new LocationMarker(lng, lat, renderableNode);
    }

    private void attachLocationMarkerToScene(LocationMarker locationMarker) {
        // Adding the marker to list
        locationScene.mLocationMarkers.add(locationMarker);
        locationScene.refreshAnchors();
    }

    private void detachLocationMarkerFromScene(LocationMarker locationMarker) {
        if (locationScene.mLocationMarkers.contains(locationMarker)) {
            locationScene.mLocationMarkers.remove(locationMarker);
            locationMarker.anchorNode.getAnchor();
            locationMarker.anchorNode.getAnchor().detach();
            locationMarker.anchorNode.setEnabled(false);
            locationMarker.anchorNode.setAnchor(null);
            locationMarker.anchorNode = null;
            locationScene.refreshAnchors();
        }
    }

    private LocationMarker createDestinationPoiMarker(double lat, double lng) {
        LocationMarker poiLocationMarker = getBaseLocationMarker(lat, lng, getDestinationPoiViewNode());
        poiLocationMarker.setGradualScalingMaxScale(0.8f);
        poiLocationMarker.setGradualScalingMinScale(0.2f);
        poiLocationMarker.setScalingMode(LocationMarker.ScalingMode.GRADUAL_FIXED_SIZE);

        // An example "onRender" event, called every frame
        // Updates the layout with the markers distance
        poiLocationMarker.setRenderEvent(node -> {
            View eView = poiViewRenderable.getView();
            TextView distanceTextView = eView.findViewById(R.id.textView2);
            distanceTextView.setText(node.getDistance() + " M");
        });

        return poiLocationMarker;
    }

    private static Node getBaseModelRenderableNode(Renderable renderable) {
        Node base = new Node();
        base.setRenderable(renderable);
        return base;
    }

    private static Node getBaseViewRenderableNode(ViewRenderable renderable) {
        Node base = new Node();
        base.setRenderable(renderable);
        return base;
    }

    private Node getDestinationPoiViewNode() {
        // create and configure node
        Node dstPoiNode = getBaseViewRenderableNode(poiViewRenderable);
        dstPoiNode.setName("dst_poi");
        // configure node view
        View eView = poiViewRenderable.getView();
        eView.setOnTouchListener((v, event) -> {
            DisplayMessagesUtils.showToastMsg(getApplicationContext(), "Location marker touched");
            return false;
        });
        return dstPoiNode;
    }

    private Node getArrowModelNode() {
        // create and configure node
        Node arrowNode = getBaseModelRenderableNode(arrowRenderable);
        arrowNode.setName("arrow");
        arrowNode.setLocalPosition(Vector3.zero());
        arrowNode.setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0f, 0f), 30f));
        arrowNode.setOnTapListener((v, event) -> {
            DisplayMessagesUtils.showToastMsg(getApplicationContext(), "Arrow touched");
        });
        return arrowNode;
    }

    @Override
    public void onStart() {
        super.onStart();
        navMapView.onStart();
    }

    /**
     * Make sure we call locationScene.resume();
     */
    @Override
    protected void onResume() {
        super.onResume();

        ServicesRepository.getInstance().startService(getApplicationContext(), GpsImuService.class, () -> {
            ServicesRepository.getInstance().getService(GpsImuService.class, serviceInstance -> {
                serviceInstance.registerImuListener(imuListener);
                serviceInstance.registerGpsLocationListener(gpsLocationListener);
            });
        });

        navMapView.onResume();

        if (locationScene != null) {
            locationScene.onResume();
        }

        if (arSceneView.getSession() == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                boolean arcoreIsInstalled = LibsUtils.ARCore.requestInstalling(this);
                if (arcoreIsInstalled) {
                    // create session
                    arCoreSession = LibsUtils.ARCore.createARCoreSession(this, true);
                    sharedCamera = arCoreSession.getSharedCamera();
                    arSceneView.setupSession(arCoreSession);
                }
            } catch (UnavailableException e) {
                String excMsg = LibsUtils.ARCore.handleException(e);
                Log.d(TAG, excMsg);
            }
        }

        try {
            arSceneView.resume();
        } catch (CameraNotAvailableException ex) {
            displayError(this, "Unable to get camera", ex);
            DisplayMessagesUtils.showToastMsg(this, "Unable to get ARCore camera");
            finish();
            return;
        }
    }

    /**
     * Make sure we call locationScene.pause();
     */
    @Override
    public void onPause() {
        super.onPause();

        ServicesRepository.getInstance().getService(GpsImuService.class, serviceInstance -> {
            serviceInstance.unregisterImuListener(imuListener);
            serviceInstance.unregisterGpsLocationListener(gpsLocationListener);
        });
        ServicesRepository.getInstance().stopService(getApplicationContext(), GpsImuService.class);

        navMapView.onPause();

        if (locationScene != null) {
            locationScene.onPause();
        }

        arSceneView.pause();
    }

    @Override
    public void onStop() {
        super.onStop();
        navMapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        navMapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        navMap.shutdown();
        navMapView.onDestroy();
        arSceneView.destroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        // all permissions can be checked and allowed in previous calling activity
        // if not then use it
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        DisplayUtils.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    public static void displayError(final Context context, final String errorMsg, @Nullable final Throwable problem) {
        final String tag = context.getClass().getSimpleName();
        final String toastText;
        if (problem != null && problem.getMessage() != null) {
            Log.e(tag, errorMsg, problem);
            toastText = errorMsg + ": " + problem.getMessage();
        } else if (problem != null) {
            Log.e(tag, errorMsg, problem);
            toastText = errorMsg;
        } else {
            Log.e(tag, errorMsg);
            toastText = errorMsg;
        }

        DisplayMessagesUtils.showToastMsg(context, toastText);
    }
}
