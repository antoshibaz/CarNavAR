package com.app.carnavar;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.app.carnavar.ar.arcorelocation.LocationMarker;
import com.app.carnavar.ar.arcorelocation.LocationScene;
import com.app.carnavar.ar.arcorelocation.rendering.LocationNode;
import com.app.carnavar.ar.arcorelocation.rendering.LocationNodeRender;
import com.app.carnavar.hal.sensors.SensorTypes;
import com.app.carnavar.maps.NavMap;
import com.app.carnavar.services.ServicesRepository;
import com.app.carnavar.services.gpsimu.GpsImuService;
import com.app.carnavar.services.gpsimu.GpsImuServiceInterfaces;
import com.app.carnavar.utils.android.DisplayMessagesUtils;
import com.app.carnavar.utils.android.DisplayUtils;
import com.app.carnavar.utils.android.LibsUtils;
import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.maps.MapView;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;


public class ArActivity extends AppCompatActivity {

    public static final String TAG = "ArActivity";

    private boolean installRequested;
    private boolean hasFinishedLoading = false;

    private Snackbar loadingMessageSnackbar = null;

    private Session arCoreSession;
    private ArSceneView arSceneView;

    private SharedCamera sharedCamera;

    // ARCore-Location scene world
    private LocationScene locationScene;

    // Renderables objects (views, models and other)
    private ViewRenderable poiArView;

    private MapView navMapView;
    private NavMap navMap;

    private GpsImuServiceInterfaces.ImuListener imuListener = (values, sensorType, timeNanos) -> {
        if (sensorType == SensorTypes.ORIENTATION_ROTATION_ANGLES) {
            if (navMap != null)
                navMap.updateOrientation(values[0]);
            if (locationScene != null)
                locationScene.updateBearing(values[0]);
            Log.d(TAG, " bearing=" + String.valueOf(values[0]));
        }
    };

    private GpsImuServiceInterfaces.GpsLocationListener gpsLocationListener = location -> {
        if (locationScene != null) {
            locationScene.updateGpsLocation(location);
        }
    };

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

        // get data from previous activity
        Intent intent = getIntent();
        final double[] dstPoiMarker = Objects.requireNonNull(intent.getExtras()).getDoubleArray("destination_marker");

        // create arcore scene view and custom session
        arSceneView = findViewById(R.id.ar_scene_view);
        try {
            arCoreSession = LibsUtils.ARCore.createARCoreSession(this, true);
            sharedCamera = arCoreSession.getSharedCamera();
            Log.d(TAG, "Selected ARCore camera -> " + LibsUtils.ARCore.handleCameraConfig(arCoreSession.getCameraConfig()));
            // TODO: shared camera image capturing
            // When ARCore is running, make sure it also updates our CPU image surface.
            //    sharedCamera.setAppSurfaces(this.cameraId, Arrays.asList(cpuImageReader.getSurface()));
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
        navMap.setNavMapInitializedListener(new NavMap.NavMapInitializedListener() {
            @Override
            public void onSuccess() {
                navMap.addMarker(Point.fromLngLat(dstPoiMarker[0], dstPoiMarker[1]));
            }
        });

        // Build a renderable from a 2D View
        CompletableFuture<ViewRenderable> poiLayout =
                ViewRenderable.builder()
                        .setView(this, R.layout.poi_ar_view)
                        .build();

        CompletableFuture.allOf(
                poiLayout)
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
                                poiArView = poiLayout.get();
                                hasFinishedLoading = true;

                            } catch (InterruptedException | ExecutionException ex) {
                                displayError(this, "Unable to load renderables", ex);
                            }

                            return null;
                        });

        // Set an update listener on the Scene that will hide the loading message once a Plane is
        // detected
        arSceneView
                .getScene()
                .addOnUpdateListener(
                        frameTime -> {
                            if (!hasFinishedLoading) {
                                return;
                            }

                            if (locationScene == null) {
                                // If our locationScene object hasn't been setup yet, this is a good time to do it
                                // We know that here, the AR components have been initiated
                                // speed 150 km/h = 42 m/s | refresh - 10-50ms
                                locationScene = new LocationScene(this, arSceneView);
//                                ServicesRepository.getInstance().getService(GpsImuService.class, serviceInstance -> {
//                                    serviceInstance.retrieveCallingLastGpsLocation(gpsLocationListener);
//                                });
                                locationScene.refreshAnchors();
//                                locationScene.setAnchorRefreshInterval(30);
                                locationScene.setRefreshAnchorsAsLocationChanges(true);

                                // Now lets create our location markers.
                                // First, a layout
                                LocationMarker poiLocationMarker = new LocationMarker(
                                        dstPoiMarker[0], dstPoiMarker[1],
                                        getPoiArViewNode()
                                );
                                poiLocationMarker.setGradualScalingMaxScale(0.8f);
                                poiLocationMarker.setGradualScalingMinScale(0.2f);
                                poiLocationMarker.setScalingMode(LocationMarker.ScalingMode.GRADUAL_FIXED_SIZE);

                                // An example "onRender" event, called every frame
                                // Updates the layout with the markers distance
                                poiLocationMarker.setRenderEvent(new LocationNodeRender() {
                                    @Override
                                    public void render(LocationNode node) {
                                        View eView = poiArView.getView();
                                        TextView distanceTextView = eView.findViewById(R.id.textView2);
                                        distanceTextView.setText(node.getDistance() + " M");
                                    }
                                });
                                // Adding the marker to list
                                locationScene.mLocationMarkers.add(poiLocationMarker);
                            }

                            Frame frame = arSceneView.getArFrame();

                            if (frame == null) {
                                return;
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

//                            if (loadingMessageSnackbar != null) {
//                                for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
//                                    if (plane.getTrackingState() == TrackingState.TRACKING) {
//                                        hideLoadingMessage();
//                                    }
//                                }
//                            }
                        });

    }

    private Node getPoiArViewNode() {
        Node base = new Node();
        base.setRenderable(poiArView);
        Context c = this;
        // Add  listeners etc here
        View eView = poiArView.getView();
        eView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Toast.makeText(c, "Location marker touched", Toast.LENGTH_LONG).show();
                return false;
            }
        });

        return base;
    }

    private void showLoadingMessage() {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar.isShownOrQueued()) {
            return;
        }

        loadingMessageSnackbar =
                Snackbar.make(
                        ArActivity.this.findViewById(android.R.id.content),
                        "Hello",
                        Snackbar.LENGTH_INDEFINITE);
        loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        loadingMessageSnackbar.show();
    }

    private void hideLoadingMessage() {
        if (loadingMessageSnackbar == null) {
            return;
        }

        loadingMessageSnackbar.dismiss();
        loadingMessageSnackbar = null;
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

        if (arSceneView.getSession() != null) {
//            showLoadingMessage();
        }

        navMapView.onResume();

        ServicesRepository.getInstance().startService(getApplicationContext(), GpsImuService.class, () -> {
            ServicesRepository.getInstance().getService(GpsImuService.class, serviceInstance -> {
                serviceInstance.registerImuListener(imuListener);
                serviceInstance.registerGpsLocationListener(gpsLocationListener);
            });
        });
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

        if (locationScene != null) {
            locationScene.onPause();
        }

        arSceneView.pause();
        navMapView.onPause();
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
        arSceneView.destroy();
        navMapView.onDestroy();
        navMap.shutdown();
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
