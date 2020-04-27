package com.app.carnavar;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.app.carnavar.ar.ArDrawer;
import com.app.carnavar.ar.arcorelocation.LocationMarker;
import com.app.carnavar.ar.arcorelocation.LocationScene;
import com.app.carnavar.cv.CvInferenceThread;
import com.app.carnavar.hal.sensors.SensorTypes;
import com.app.carnavar.maps.NavMap;
import com.app.carnavar.maps.NavMapRoute;
import com.app.carnavar.services.ServicesRepository;
import com.app.carnavar.services.gpsimu.GpsImuService;
import com.app.carnavar.services.gpsimu.GpsImuServiceInterfaces;
import com.app.carnavar.ui.OverlayView;
import com.app.carnavar.utils.android.DisplayMessagesUtils;
import com.app.carnavar.utils.android.DisplayUtils;
import com.app.carnavar.utils.android.LibsUtils;
import com.app.carnavar.utils.filters.LocationFilters;
import com.app.carnavar.utils.maps.CoordinatesUtils;
import com.app.carnavar.utils.maps.MapsUtils;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
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
import com.google.ar.sceneform.rendering.Light;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.mapbox.api.directions.v5.models.LegStep;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.maps.MapView;

import java.util.LinkedList;
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
    private SharedCamera sharedCamera;
    // ARCore-Location scene world
    private LocationScene locationScene;
    // Renderables objects (views, models and other)
    private ViewRenderable poiViewRenderable;
    private Renderable arrowRenderable;

    private MapView navMapView;
    private NavMap navMap;
    private OverlayView overlayView;

    private LocationMarker targetDestinationMarker;
    private Location lastLocation;
    private float[] currentOrientationAngles = new float[3];

    private NavMapRoute currentNavRoute;

    private LocationFilters.GeoLocationHeuristicFilter geoLocationHeuristicFilter = new LocationFilters.GeoLocationHeuristicFilter();

    private CvInferenceThread cvInferenceThread;

    private GpsImuServiceInterfaces.ImuListener imuListener = (values, sensorType, timeNanos) -> {
        if (sensorType == SensorTypes.ORIENTATION_ROTATION_ANGLES) {
            updateForOrientation(values);
        } else if (sensorType == SensorTypes.MAGNETIC_FIELD) {
            //System.arraycopy(values, 0, magnetValues, 0, values.length);
        } else if (sensorType == SensorTypes.ORIENTATION_ROTATION_MATRIX) {
            //System.arraycopy(values, 0, currentPoseRotMatrix, 0, values.length);
        }
    };

    private GpsImuServiceInterfaces.GpsLocationListener gpsLocationListener = this::updateForLocation;

    private OverlayView.DrawCallback drawCallback;

    private float deltaBearing = 0;
    private int gpsBearingEstablishmentFactor = 0;
    private float calibratedBearing = 0;

    private Pose lastPose;

    private void updateForOrientation(float[] orientationAngles) {
        float prevOrientationBearing = currentOrientationAngles[0];
        System.arraycopy(orientationAngles, 0, currentOrientationAngles, 0, orientationAngles.length);
        float orientationBearing = currentOrientationAngles[0];
        float orientationDiff = Math.abs(CoordinatesUtils.getMinSignedAnglesDiff(prevOrientationBearing,
                orientationBearing));
//        boolean updRequest = false;
//        if (orientationDiff >= 0.1f) { // rotation patience - throttling
//            updRequest = true;
//        }

        int gpsBearing = 0;
        if (lastLocation != null && geoLocationHeuristicFilter.bearingIsEstablished()) {
            gpsBearingEstablishmentFactor++;
            if (gpsBearingEstablishmentFactor > 1) { // bearing establishment patience
                gpsBearing = (int) lastLocation.getBearing();
                float bearingDiff = CoordinatesUtils.getMinSignedAnglesDiff(orientationBearing, gpsBearing);
                if (Math.abs(bearingDiff) >= 5) { // abs error between sensor and gps bearings
                    deltaBearing = bearingDiff;
                }
            }
        } else {
            gpsBearingEstablishmentFactor = 0;
        }

        calibratedBearing = (int) (((orientationBearing + deltaBearing) + 360) % 360);

        if (navMap != null && navMapInitSuccess) {
            if (calibratedBearing == 0) {
                navMap.updateOrientation(360);
            } else {
                navMap.updateOrientation(calibratedBearing);
            }
        }

        if (locationScene != null) {
            float arSceneBearDiff = CoordinatesUtils.getMinSignedAnglesDiff((int) locationScene.getCurrentBearing(),
                    calibratedBearing); // check rot in locationScene
            locationScene.updateBearing(calibratedBearing);
        }

        Log.d(TAG, "diff=" + orientationDiff + " raw_bear=" + orientationBearing + " gps_bear=" + gpsBearing +
                " ar_calib_bear=" + calibratedBearing + " delta_bear=" + deltaBearing);
        if (arSceneView != null) {
            Log.d("cam", "" + arSceneView.getScene().getCamera().getWorldPosition().y);
        }
    }

    private void updateForLocation(Location location) {
        Location filteredLocation;
        if (lastLocation == null) { // cold start: first location gives low precision
            filteredLocation = location;
        } else {
            filteredLocation = geoLocationHeuristicFilter.process(location);
        }

        if (navMap != null && navMapInitSuccess) {
            if (filteredLocation != lastLocation) {
                Location updLocation = new Location(filteredLocation);
                updLocation.setBearing(((int) calibratedBearing == 0) ? 360f : calibratedBearing);
                navMap.updateLocation(updLocation);
            }

            if (navMap.getCurrentDestinationPoint() == null) {
                // get data from previous activity
                Intent intent = getIntent();
                final double[] dstPoiMarker = Objects.requireNonNull(intent.getExtras()).getDoubleArray("destination_marker");
                if (dstPoiMarker != null) {
                    navMap.replaceMarker(Point.fromLngLat(dstPoiMarker[0], dstPoiMarker[1]));
                }
            }
        }

        if (locationScene != null && (locationScene.getCurrentLocation() != filteredLocation
                || filteredLocation != lastLocation)) {
            locationScene.updateGpsLocation(filteredLocation);
        }

        lastLocation = filteredLocation;
        Log.d(TAG, "Filtered location -> bearStab" + geoLocationHeuristicFilter.isBearingIsStable() + " gpsBearEstablished=" + geoLocationHeuristicFilter.bearingIsEstablished()
                + " " + MapsUtils.toString(lastLocation));
    }

    private void init(Bundle savedInstanceState) {
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

        // create arcore session and ar scene view
        arSceneView = findViewById(R.id.ar_scene_view);
        try {
            arCoreSession = LibsUtils.ARCore.createARCoreSession(this, true);
            sharedCamera = arCoreSession.getSharedCamera();
            Log.d(TAG, "Selected ARCore camera -> " + LibsUtils.ARCore.handleCameraConfig(arCoreSession.getCameraConfig()));
        } catch (UnavailableException e) {
            String excMsg = LibsUtils.ARCore.handleException(e);
            Log.e(TAG, excMsg);
            DisplayMessagesUtils.showToastMsg(this, excMsg);
            finish();
        }
        arSceneView.setupSession(arCoreSession);

        // Create and init renderables objects
        // Build a renderable from a 2D View
        CompletableFuture<ViewRenderable> poiLayout =
                ViewRenderable.builder()
                        .setView(this, R.layout.poi_ar_view)
                        .build();
        // Build a renderable for a 3D Model
        CompletableFuture<ModelRenderable> arrowModel = ModelRenderable.builder()
                .setSource(this, Uri.parse("3dmodels/RLab-arrow.sfb"))
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
                                MaterialFactory.makeOpaqueWithColor(getApplicationContext(),
                                        new com.google.ar.sceneform.rendering.Color(android.graphics.Color.parseColor("#FF00FFE5")))
                                        .thenAccept(material -> {
                                            arrowRenderable.setMaterial(material);
                                        });
                                hasRenderersFinishedLoading = true;
                            } catch (InterruptedException | ExecutionException ex) {
                                displayError(this, "Unable to load renderables", ex);
                            }

                            return null;
                        });

        // disable plane renderer as it not use in this scenario
        arSceneView.getPlaneRenderer().setEnabled(false);
        arSceneView.getScene().getSunlight().setEnabled(true);
        // Set an update listener looper on the Scene
        arSceneView.getScene().addOnUpdateListener(frameTime -> arUpdateLooper());

        // init maps
        navMapView = findViewById(R.id.miniNavMapView);
        navMapView.onCreate(savedInstanceState);
        navMap = new NavMap(navMapView, getString(R.string.mapbox_map_style_streets));
        navMap.setNavMapInitializedListener(() -> {
            navMapInitSuccess = true;
            Log.d(TAG, "NavMap is success loaded");
//            Location mockMyLoc = new Location("");
//            mockMyLoc.setLatitude(54.772165);
//            mockMyLoc.setLongitude(31.684625);
//            gpsLocationListener.onGpsLocationReturned(mockMyLoc);
        });
        navMap.setDestinationMarkerChangedListener((newDstPoint, pointFeatures) -> {
            navMap.updateRoutesFromMyLocationTo(newDstPoint);
            if (hasRenderersFinishedLoading) {
                LocationMarker newDestinationMarker = createDestinationPoiMarker(newDstPoint.latitude(),
                        newDstPoint.longitude());
                if (locationScene != null) {
                    // call to render ar now
                    locationScene.detachAllLocationMarkers();
                    targetDestinationMarker = newDestinationMarker;
                    locationScene.attachLocationMarker(targetDestinationMarker);
                } else {
                    // put to queue for rendering
                    targetDestinationMarker = newDestinationMarker;
                }
            }
        });
        navMap.setUpdateRoutesListener((currentTargetRoute, currentAvailableRoutes) -> {
            navMap.trackingMyLocation(150);
            if (hasRenderersFinishedLoading && locationScene != null) {
                currentNavRoute = new NavMapRoute(currentTargetRoute);
                StringBuilder sb = new StringBuilder();
                for (LegStep step : currentNavRoute.getManeuverPoints()) {
                    sb.append(step.maneuver().type()).append(" ");
                }
                Log.d(TAG, "maneuvers -> " + sb.toString());
                addManeuverMarkersToArScene(currentNavRoute.getManeuverPoints());
            }
        });

        overlayView = findViewById(R.id.overlay);
        overlayView.addDrawCallback(this::overlayUpdateLooper);
    }

    private void updateOverlay(long updateTimeInterval) {
        new Handler().postDelayed(() -> {
            if (overlayView != null && locationScene != null
                    && hasRenderersFinishedLoading && navMapInitSuccess) {
                overlayView.postInvalidate();
            }
            updateOverlay(updateTimeInterval);
        }, updateTimeInterval);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init(savedInstanceState);
        updateOverlay(33L);
    }

    private void arUpdateLooper() {
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
            locationScene.setOffsetOverlapping(false);
            locationScene.setRemoveOverlapping(false);

            // Now lets create our location markers
            if (targetDestinationMarker != null
                    && !locationScene.mLocationMarkers.contains(targetDestinationMarker)) {
                locationScene.attachLocationMarker(targetDestinationMarker);
            }

//            double[] latLng = MapsUtils.getDstLocationByBearingAndDistance(dstPoiMarker[1], dstPoiMarker[0], 180, 100);
//            LocationMarker arrowNode = new LocationMarker(latLng[1], latLng[0], getArrowModelNode());
//            arrowNode.setRenderEvent(new LocationNodeRender() {
//                @Override
//                public void render(LocationNode node) {
//                    for (Node n : node.getChildren()) {
//                        n.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 90f));
//                    }
//                }
//            });
//            locationScene.mLocationMarkers.add(arrowNode);
        }

        Frame frame = arSceneView.getArFrame();
        if (frame == null) {
            return;
        }

        // image analysis
        try (Image image = frame.acquireCameraImage()) {
            if (cvInferenceThread != null) {
                cvInferenceThread.processFrame(image);
            }
        } catch (NotYetAvailableException e) {
        }

//        frame.getCamera().getDisplayOrientedPose() getProjectionMatrix() getViewMatrix()
//        Pose pose1 = arSceneView.getArFrame().getAndroidSensorPose();
//        Pose pose2 = arSceneView.getArFrame().getCamera().getPose();
//        Pose pose3 = arSceneView.getArFrame().getCamera().getDisplayOrientedPose();
//        float[] rotVec1 = pose1.getRotationQuaternion();
//        float[] rotMat1 = new float[9]; // or pose1.toMatrix(...)
//        SensorManager.getRotationMatrixFromVector(rotMat1, rotVec1);
//        float[] angles1 = new float[3];
//        SensorManager.getOrientation(rotMat1, angles1);
//        angles1[0] = ((float) Math.toDegrees(angles1[0]) + 360f) % 360f;
//        angles1[1] = (float) Math.toDegrees(angles1[1]);
//        angles1[2] = (float) Math.toDegrees(angles1[2]);
//        if (loadingMessageSnackbar != null) {
//            loadingMessageSnackbar.setText("1:" + Math.round(angles1[0]) + " 2:" + Math.round(angles1[1]) + " 3:" + Math.round(angles1[2]));
//        }
//        arSceneView.getScene().getCamera().getWorldPosition();

        if (locationScene != null && locationScene.getRefreshListener() == null) {
            locationScene.setRefreshListener(() -> {
                lastPose = frame.getCamera().getDisplayOrientedPose();
                if (currentNavRoute != null) {
                    calcRouteWorldPoints(currentNavRoute);
                    if (drawCallback == null) {
                        drawCallback = this::drawRoutePoints;
                        overlayView.addDrawCallback(drawCallback);
                    }
                }
            });
        }

        if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        // planes detecting
//        for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
//            if (plane.getTrackingState() == TrackingState.TRACKING) {
//                hideLoadingMessage();
//            }
//        }

        if (locationScene != null) {
            locationScene.processFrame(frame);
        }
    }

    private void overlayUpdateLooper(Canvas canvas) {
//        if (segImg != null) {
//            Bitmap bitmap = Bitmap.createScaledBitmap(segImg, canvas.getWidth(), canvas.getHeight(), true);
//            canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
//        }

        if (lastLocation != null && targetDestinationMarker != null
                && targetDestinationMarker.anchorNode != null
                && targetDestinationMarker.anchorNode.getAnchor() != null) {
            Location dstLoc = new Location("");
            dstLoc.setLongitude(targetDestinationMarker.longitude);
            dstLoc.setLatitude(targetDestinationMarker.latitude);
            //dstLoc.setAltitude(0);
            //float[] p = new float[]{0, -1, -5, 1};

            Size screenSize = new Size(canvas.getWidth(), canvas.getHeight());
            // Approach 1
            float[] pose = targetDestinationMarker.anchorNode.getAnchor().getPose().getTranslation();
//            Vector3 dstVec = targetDestinationMarker.node.getWorldPosition();
            Vector3 dstVec = new Vector3();
            dstVec.x = pose[0];
            dstVec.y = pose[1];
            dstVec.z = pose[2];
            //dstVec.y = -3f;
            Vector3 screenPoiVec = CoordinatesUtils.worldToScreenPoint(dstVec,
                    arSceneView.getScene().getCamera().getProjectionMatrix(),
                    arSceneView.getScene().getCamera().getViewMatrix(), screenSize);
            ArDrawer.drawOverlayNavigationBeacon(screenPoiVec, canvas,
                    targetDestinationMarker.anchorNode.getDistance(), true);
        }
    }

    private List<Vector3> drawingRoutePointsList;
    private List<Integer> distances;

    private void calcRouteWorldPoints(NavMapRoute route) {
        if (lastLocation == null || locationScene == null
                || !hasRenderersFinishedLoading || !navMapInitSuccess) return;

        int i = 0;
        drawingRoutePointsList = new LinkedList<>();
        distances = new LinkedList<>();
        for (Point p : route.getRoutePoints()) {
            i++;
            int routePointDistance = (int) Math.round(
                    MapsUtils.euclideanPythagoreanDistance(p.latitude(),
                            p.longitude(),
                            lastLocation.getLatitude(),
                            lastLocation.getLongitude())
            );

            if (routePointDistance > 100) {
                continue;
            }

            float routePointBearing = (float) MapsUtils.calcBearing(
                    lastLocation.getLatitude(),
                    lastLocation.getLongitude(),
                    p.latitude(),
                    p.longitude());

            float bearing = ((routePointBearing - (float) calibratedBearing) + 360f) % 360;
            double rotation = Math.floor(bearing);
            float z = -routePointDistance;
            double rotationRadian = Math.toRadians(rotation);
            float zRotated = (float) (z * Math.cos(rotationRadian));
            float xRotated = (float) -(z * Math.sin(rotationRadian));
            float y = arSceneView.getScene().getCamera().getWorldPosition().y - 5f;
            Log.d("1y=", "" + y);
            Pose translation = Pose.makeTranslation(xRotated, 0, zRotated);
            Pose routePointPose = lastPose
                    .compose(translation)
                    .extractTranslation();
            Log.d("2y=", "" + lastPose.ty());
            Log.d("3y=", "" + routePointPose.ty());
            Vector3 routePointWorld = new Vector3();
            routePointWorld.x = routePointPose.tx();
            routePointWorld.y = y;
            routePointWorld.z = routePointPose.tz();
            drawingRoutePointsList.add(routePointWorld);
            distances.add(routePointDistance);
            Log.d(TAG, " world" + routePointWorld.toString());
        }
    }

    private void drawRoutePoints(Canvas canvas) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(24);
        Size screenSize = new Size(canvas.getWidth(), canvas.getHeight());
        int i = 0;
        for (Vector3 routePointWorld : drawingRoutePointsList) {
            Vector3 routePointScreen = CoordinatesUtils.worldToScreenPoint(routePointWorld,
                    arSceneView.getScene().getCamera().getProjectionMatrix(),
                    arSceneView.getScene().getCamera().getViewMatrix(), screenSize);
            if (routePointScreen.z >= 0) {
                canvas.drawCircle(routePointScreen.x, routePointScreen.y, 15, paint);
                canvas.drawText(String.valueOf(distances.get(i)), routePointScreen.x, routePointScreen.y - 25, paint);
                Log.d(TAG, " x=" + routePointScreen.x + " y=" + routePointScreen.y + " dist=" + distances.get(i));
                i++;
            }
        }
        Log.d(TAG, " i=" + i);
    }

    private void addManeuverMarkersToArScene(List<LegStep> routeManeuverPoints) {
        for (LegStep step : routeManeuverPoints) {
            // filtering required maneuver types
            if (NavMapRoute.mapToManeuverType(step.maneuver().type()) != NavMapRoute.ManeuverType.TURN
                    && NavMapRoute.mapToManeuverType(step.maneuver().type()) != NavMapRoute.ManeuverType.MERGE) {
                continue;
            }

            LocationMarker maneuverMarker = new LocationMarker(step.maneuver().location().longitude(),
                    step.maneuver().location().latitude(), getArrowModelNode());
            maneuverMarker.setHeight(0f);
            //maneuverMarker.setScalingMode(LocationMarker.ScalingMode.NO_SCALING);
            maneuverMarker.setScaleModifier(0.7f);
            maneuverMarker.setScalingMode(LocationMarker.ScalingMode.GRADUAL_FIXED_SIZE);
            maneuverMarker.setOnlyRenderWhenWithin(150);
            double bearFrom = step.maneuver().bearingBefore();
            double bearTo = step.maneuver().bearingAfter();
            float bearing = (float) (((bearTo - calibratedBearing) + 360f) % 360); //calibBear==bearFrom
            float rotation = (float) Math.floor(360 - bearing);
            maneuverMarker.setRenderEvent(node -> {
                for (Node n : node.getChildren()) {
                    maneuverMarker.anchorNode.setLight(
                            Light.builder(Light.Type.SPOTLIGHT).setColor(
                                    new com.google.ar.sceneform.rendering.Color(255, 255, 255)).build());
                    n.setLight(
                            Light.builder(Light.Type.SPOTLIGHT).setColor(
                                    new com.google.ar.sceneform.rendering.Color(255, 255, 255)).build());
                    n.setWorldRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), rotation));
                }
            });
            locationScene.attachLocationMarker(maneuverMarker);
            Log.d(TAG, "maneuver marker is added");
        }
    }

    private LocationMarker createDestinationPoiMarker(double lat, double lng) {
        LocationMarker poiLocationMarker = LocationScene.getBaseLocationMarker(lat, lng, getDestinationPoiViewNode());
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

    private Node getDestinationPoiViewNode() {
        // create and configure node
        Node dstPoiNode = ArDrawer.getBaseViewRenderableNode(poiViewRenderable);
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
        Node arrowNode = ArDrawer.getBaseModelRenderableNode(arrowRenderable);
        arrowNode.setName("arrow");
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

    SensorManager sensorManager;
    float[] rawValues = new float[4];
    float[] rot = new float[16];
    SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                System.arraycopy(event.values, 0, rawValues, 0, rawValues.length);
                SensorManager.getRotationMatrixFromVector(rot, rawValues);

                int worldAxisForDeviceAxisX;
                int worldAxisForDeviceAxisY;
                // Remap the matrix based on current device/activity rotation
                // for vertical portrait device (device ax Y = Earth ax Z) as default
                switch (((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                        break;
                }

                float[] angles2 = new float[3];
                float[] adjR = new float[16];
                SensorManager.remapCoordinateSystem(rot, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjR);
                SensorManager.getOrientation(adjR, angles2);
                angles2[0] = (float) ((Math.toDegrees(angles2[0]) + 360f) % 360f);
                Log.d("orient", "heading=" + angles2[0]);
                imuListener.onImuReturned(angles2, SensorTypes.ORIENTATION_ROTATION_ANGLES, event.timestamp);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    /**
     * Make sure we call locationScene.resume();
     */
    @Override
    protected void onResume() {
        super.onResume();
//        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
//        sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
//                50000);

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

//        cvInferenceThread = CvInferenceThread.createAndStart(this);
//        cvInferenceThread.setInferenceCallback(inferencedImage -> {
//            if (overlayView != null) {
//                segImg = inferencedImage;
//                overlayView.postInvalidate();
//            }
//        });
    }

    /**
     * Make sure we call locationScene.pause();
     */
    @Override
    public void onPause() {
        super.onPause();
        //sensorManager.unregisterListener(sensorEventListener);

        ServicesRepository.getInstance().getService(GpsImuService.class, serviceInstance -> {
            serviceInstance.unregisterImuListener(imuListener);
            serviceInstance.unregisterGpsLocationListener(gpsLocationListener);
        });
        ServicesRepository.getInstance().stopService(getApplicationContext(), GpsImuService.class);

//        cvInferenceThread.close();
//        cvInferenceThread = null;

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
