package com.app.carnavar.maps;

import android.content.Context;
import android.graphics.Bitmap;
import android.location.Location;
import android.util.Log;
import android.widget.Toast;

import com.app.carnavar.utils.android.ThemeUtils;
import com.app.carnavar.utils.maps.MapsUtils;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.directions.v5.models.LegStep;
import com.mapbox.api.directions.v5.models.RouteLeg;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.core.constants.Constants;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.utils.PolylineUtils;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.LocationComponentConstants;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions;
import com.mapbox.mapboxsdk.plugins.building.BuildingPlugin;
import com.mapbox.navigator.BannerInstruction;
import com.mapbox.navigator.VoiceInstruction;
import com.mapbox.services.android.navigation.ui.v5.camera.CameraUpdateMode;
import com.mapbox.services.android.navigation.ui.v5.camera.NavigationCamera;
import com.mapbox.services.android.navigation.ui.v5.camera.NavigationCameraUpdate;
import com.mapbox.services.android.navigation.ui.v5.map.NavigationMapboxMap;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigationOptions;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.offroute.OffRouteListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteLegProgress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NavMap {

    public static final String TAG = NavMap.class.getSimpleName();

    private MapView mapView;
    private MapboxMap map;
    //private NavigationMapboxMap navigationMapboxMap;
    //NavigationView
    private MapboxNavigation navigation;

    private NavMapMarkerManager markerManager;

    private LocationComponent locationComponent;
    private LocationEngine locationEngine;

    private NavigationCamera mapCamera;
    private NavigationMapRoute mapRoute;

    private DirectionsRoute currentTargetRoute;

    private Location lastLocation;
    private Point currentDestinationPoint;

    private NavMapInitializedListener navMapInitializedListener;
    private List<DirectionsRoute> currentAvailableRoutes;
    private DestinationMarkerChangedListener destinationMarkerChangedListener;
    private UpdateRoutesListener updateRoutesListener;

    public interface NavMapInitializedListener {
        void onSuccess();
    }

    public void setNavMapInitializedListener(NavMapInitializedListener navMapInitializedListener) {
        this.navMapInitializedListener = navMapInitializedListener;
    }

    public interface DestinationMarkerChangedListener {
        void onDstMarkerChanged(Point newDstPoint, @Nullable CarmenFeature pointFeatures);
    }

    public void setDestinationMarkerChangedListener(DestinationMarkerChangedListener destinationMarkerChangedListener) {
        this.destinationMarkerChangedListener = destinationMarkerChangedListener;
    }

    public interface UpdateRoutesListener {
        void onRoutesUpdated(DirectionsRoute currentTargetRoute, List<DirectionsRoute> currentAvailableRoutes);
    }

    public void setUpdateRoutesListener(UpdateRoutesListener updateRoutesListener) {
        this.updateRoutesListener = updateRoutesListener;
    }

    /**
     * Can be used to automatically drive the map camera / route updates and arrow
     * once navigation has started.
     * <p>
     * These will automatically be removed in {@link MapboxNavigation#onDestroy()}.
     */
    public void setProgressChangeListener() {
        mapRoute.addProgressChangeListener(navigation);
        mapCamera.addProgressChangeListener(navigation);
    }

    public NavMap(MapView mapView, String styleUri) {
        this.mapView = mapView;
        this.mapView.getMapAsync(mapboxMap -> {
            map = mapboxMap;
            map.setStyle(new Style.Builder().fromUri(styleUri), style -> {
                //mapView.setMaximumFps(60);
                initLocation(mapView, map);
//                initLocationEngine(mapView);

                map.setCameraPosition(new CameraPosition.Builder()
                        .zoom(16f)
                        .tilt(45f)
                        .padding(0, (int) (map.getHeight() - map.getHeight() * 0.3), 0, 0)
                        .build());

                initNavigation();
                initNavMapRoute(navigation, mapView, map);
                initNavMapCamera(map, navigation, locationComponent);
                initNavMapMarkerManager(mapView, map);

                map.addOnMapClickListener(mapClickListener);
                navigation.addOffRouteListener(offRouteListener);

                BuildingPlugin buildingPlugin = new BuildingPlugin(mapView, map, style,
                        LocationComponentConstants.BACKGROUND_LAYER);
                buildingPlugin.setVisibility(true);
                buildingPlugin.setMinZoomLevel(10.0f);
                buildingPlugin.setOpacity(0.4f);

                if (navMapInitializedListener != null) {
                    navMapInitializedListener.onSuccess();
                }
            });
        });
    }

    public Point getCurrentDestinationPoint() {
        return currentDestinationPoint;
    }

    public void replaceMarker(CarmenFeature carmenFeature) {
        clearMarkers();
        currentDestinationPoint = (Point) carmenFeature.geometry();
        addMarker(currentDestinationPoint);
        if (destinationMarkerChangedListener != null) {
            destinationMarkerChangedListener.onDstMarkerChanged((Point) carmenFeature.geometry(),
                    carmenFeature);
        }
    }

    public void replaceMarker(Point point) {
        clearMarkers();
        currentDestinationPoint = point;
        addMarker(currentDestinationPoint);
        if (destinationMarkerChangedListener != null) {
            destinationMarkerChangedListener.onDstMarkerChanged(point, null);
        }
    }

    /**
     * Adds a marker icon on the map at the given position.
     * <p>
     * The icon used for this method can be defined in your theme with
     * the attribute <tt>navigationViewDestinationMarker</tt>.
     *
     * @param position the point at which the marker will be placed
     */
    public void addMarker(Point position) {
        markerManager.addMarkerFor(position);
    }

    /**
     * Adds a custom marker to the map based on the options provided.
     * <p>
     * Please note, the map will manage all markers added.  Calling {@link NavigationMapboxMap#clearMarkers()}
     * will clear all destination / custom markers that have been added to the map.
     *
     * @param options for the custom {@link com.mapbox.mapboxsdk.plugins.annotation.Symbol}
     */
    public void addCustomMarker(SymbolOptions options) {
        markerManager.addCustomMarkerFor(options);
    }

    /**
     * Clears all markers on the map that have been added by this class.
     * <p>
     * This will not clear all markers from the map entirely.  Does nothing
     * if no markers have been added.
     */
    public void clearMarkers() {
        markerManager.removeAllMarkerSymbols();
    }

    public void moveCamera(Point point, int duration) {
        CameraUpdate cameraPosition = CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder()
                        .target(new LatLng(point.latitude(), point.longitude()))
                        .zoom(16f)
                        .padding(new double[]{0, 0, 0, 0})
                        .build());
        NavigationCameraUpdate cameraUpdate = new NavigationCameraUpdate(cameraPosition);
        cameraUpdate.setMode(CameraUpdateMode.OVERRIDE);
        mapCamera.update(cameraUpdate, duration);
    }

    private void initLocation(MapView mapView, MapboxMap mapboxMap) {
        locationComponent = mapboxMap.getLocationComponent();
        Context context = mapView.getContext();
        Style style = mapboxMap.getStyle();
        LocationComponentActivationOptions locationComponentActivationOptions =
                LocationComponentActivationOptions.builder(context, style)
                        .useDefaultLocationEngine(false)
                        .build();
        locationComponent.activateLocationComponent(locationComponentActivationOptions);
        locationComponent.setLocationComponentEnabled(true);
        locationComponent.setRenderMode(RenderMode.GPS);
        locationComponent.setCameraMode(CameraMode.TRACKING_GPS);
        //locationComponent.setMaxAnimationFps(60);
    }

    private void initLocationEngine(MapView mapView) {
        locationEngine = LocationEngineProvider.getBestLocationEngine(mapView.getContext());
        LocationEngineRequest locationEngineRequest = new LocationEngineRequest.Builder(100L).setFastestInterval(100L).build();
        locationEngine.requestLocationUpdates(locationEngineRequest, locationEngineCallback, null);
        locationEngine.getLastLocation(locationEngineCallback);
    }

    private void initNavigation() {
        navigation = new MapboxNavigation(mapView.getContext(), Mapbox.getAccessToken(),
                MapboxNavigationOptions.builder()
                        .enableRefreshRoute(true)
                        .enableFasterRouteDetection(true)
                        .navigationNotification(null)
                        .isDebugLoggingEnabled(true)
                        .build(),
                locationEngine);
        navigation.addProgressChangeListener((location, routeProgress) -> {
            RouteLeg routeLeg = routeProgress.currentLeg();
            List<LegStep> listSteps = routeLeg.steps();
            RouteLegProgress routeLegProgress = routeProgress.currentLegProgress();
            String routeProgressStr = String.format("TraveledDist:%s RemainingDist:%s TimeRemaining:%s Completed:%s " +
                            "TotalDist:%s TotalTime:%s RemainingLegs:%s CurrLeg:%s",
                    routeProgress.distanceTraveled(),
                    routeProgress.distanceRemaining(),
                    routeProgress.durationRemaining(),
                    routeProgress.fractionTraveled(),
                    routeProgress.directionsRoute().distance(),
                    routeProgress.directionsRoute().duration(),
                    routeProgress.remainingWaypoints(),
                    routeProgress.legIndex());

            BannerInstruction bannerInstruction = routeProgress.bannerInstruction();
            VoiceInstruction voiceInstruction = routeProgress.voiceInstruction();
        });
    }

    private void initNavMapCamera(MapboxMap mapboxMap, MapboxNavigation mapboxNavigation, LocationComponent locationComponent) {
        mapCamera = new NavigationCamera(mapboxMap, mapboxNavigation, locationComponent);
    }

    private void initNavMapRoute(MapboxNavigation mapboxNavigation, MapView mapView, MapboxMap mapboxMap) {
        mapRoute = new NavigationMapRoute(mapboxNavigation, mapView, mapboxMap);
    }

    private void initNavMapMarkerManager(MapView mapView, MapboxMap mapboxMap) {
        Bitmap markerBitmap = ThemeUtils.retrieveThemeMapMarker(mapView.getContext());
        Style style = mapboxMap.getStyle();
        if (style != null) {
            SymbolManager symbolManager = new SymbolManager(mapView, mapboxMap, style);
            markerManager = new NavMapMarkerManager(symbolManager);
            style.addImage(NavMapMarkerManager.NAVMAP_MARKER_NAME, markerBitmap);
        }
    }

    public void updateLocation(Location location) {
        if (locationComponent != null) {
            locationComponent.forceLocationUpdate(location);
            lastLocation = location;
        }
    }

    public Location getLastLocation() {
        return locationComponent.getLastKnownLocation();
    }

    public void trackingMyLocation(int retToLocationDuration) {
        if (locationComponent.getLastKnownLocation() != null) {
            CameraUpdate cameraPosition = CameraUpdateFactory.newCameraPosition(
                    new CameraPosition.Builder()
                            .target(new LatLng(locationComponent.getLastKnownLocation()))
                            .tilt(45)
                            .zoom(16f)
                            .padding(new double[]{0, (int) (map.getHeight() - map.getHeight() * 0.3), 0, 0})
                            .bearing(locationComponent.getLastKnownLocation().getBearing())
                            .build());
            NavigationCameraUpdate cameraUpdate = new NavigationCameraUpdate(cameraPosition);
            cameraUpdate.setMode(CameraUpdateMode.OVERRIDE);
            mapCamera.update(cameraUpdate, retToLocationDuration, new MapboxMap.CancelableCallback() {
                @Override
                public void onCancel() {

                }

                @Override
                public void onFinish() {
                    locationComponent.setCameraMode(CameraMode.TRACKING_GPS);
                    //mapCamera.updateCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_GPS);
                }
            });
        }
    }

    public void updateOrientation(float bearing) {
        if (locationComponent != null) {
            Location lastLocation = locationComponent.getLastKnownLocation();
            if (lastLocation != null) {
                lastLocation.setBearing(bearing);
                locationComponent.forceLocationUpdate(lastLocation);
            }
        }
    }

    public void getRoutes(Point source, Point destination, Callback<DirectionsResponse> directionsRouteCallback) {
        NavigationRoute.builder(mapView.getContext())
                .accessToken(Mapbox.getAccessToken())
                .origin(source) // + bearing and tolerance
                .destination(destination)
                .alternatives(true)
                .build()
                .getRoute(directionsRouteCallback);
    }

    public void updateRoutesFromMyLocationTo(Point dst) {
        if (locationComponent.getLastKnownLocation() != null) {
            Point srcPoint = Point.fromLngLat(locationComponent.getLastKnownLocation().getLongitude(),
                    locationComponent.getLastKnownLocation().getLatitude());

            getRoutes(srcPoint, dst, buildRoutesCallback);
        }
    }

    public void drawRoute(DirectionsRoute route) {
        mapRoute.addRoute(route);
    }

    public void drawRoutes(List<DirectionsRoute> routes) {
        mapRoute.addRoutes(routes);
    }

    public void showRouteOverview(DirectionsRoute route) {
        mapCamera.updateCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_NONE);
        List<Point> routePoints = overview(route);
        animateMapboxMapForRoute(new int[]{164, 164, 164, 164}, routePoints);
    }

    private void animateMapboxMapForRoute(int[] padding, List<Point> routePoints) {
        if (routePoints.size() <= 1) {
            return;
        }

        CameraPosition resetPosition = new CameraPosition.Builder().tilt(0).bearing(0).build();
        CameraUpdate resetUpdate = CameraUpdateFactory.newCameraPosition(resetPosition);

        List<LatLng> latLngs = new ArrayList<>();
        for (Point routePoint : routePoints) {
            latLngs.add(new LatLng(routePoint.latitude(), routePoint.longitude()));
        }
        if (lastLocation != null) {
            latLngs.add(new LatLng(lastLocation));
        }
        if (currentDestinationPoint != null) {
            latLngs.add(new LatLng(currentDestinationPoint.latitude(), currentDestinationPoint.longitude()));
        }
        final LatLngBounds routeBounds = new LatLngBounds.Builder()
                .includes(latLngs)
                .build();

        final CameraUpdate overviewUpdate = CameraUpdateFactory.newLatLngBounds(
                routeBounds, padding[0], padding[1], padding[2], padding[3]
        );
        map.animateCamera(resetUpdate, 250,
                new MapboxMap.CancelableCallback() {
                    @Override
                    public void onCancel() {
                    }

                    @Override
                    public void onFinish() {
                        map.animateCamera(overviewUpdate, 750);
                    }
                }
        );
    }

    private List<Point> overview(DirectionsRoute route) {
        if (route == null) {
            return Collections.emptyList();
        }
        LineString lineString = LineString.fromPolyline(route.geometry(), Constants.PRECISION_6);
        return lineString.coordinates();
    }

    public static Point[] getRoutePoints(DirectionsRoute route) {
        ArrayList<Point> routePoints = new ArrayList<>();
        List<RouteLeg> legs = route.legs();
        if (legs != null) {
            for (RouteLeg leg : legs) {
                List<LegStep> steps = leg.steps();
                if (steps != null) {
                    for (LegStep step : steps) {
                        if (step.geometry() != null) {
                            List<Point> geometryPoints = PolylineUtils.decode(step.geometry(), Constants.PRECISION_6);
                            routePoints.addAll(geometryPoints);
                        }
                    }
                }
            }
        }

        return routePoints.toArray(new Point[0]);
    }

    public void shutdown() {
        if (locationEngine != null) {
            locationEngine.removeLocationUpdates(locationEngineCallback);
        }

        if (navigation != null) {
            navigation.onDestroy();
        }
    }

    private LocationEngineCallback<LocationEngineResult> locationEngineCallback = new LocationEngineCallback<LocationEngineResult>() {
        @Override
        public void onSuccess(LocationEngineResult result) {
            if (result.getLastLocation() != null) {
//                updateLocation(result.getLastLocation());
                Log.d(TAG, "Mapbox location -> " + MapsUtils.toString(result.getLastLocation()));
            }
        }

        @Override
        public void onFailure(@NonNull Exception exception) {
        }
    };

    private MapboxMap.OnMapClickListener mapClickListener = new MapboxMap.OnMapClickListener() {
        @Override
        public boolean onMapClick(@NonNull LatLng point) {
            if (locationComponent == null || locationComponent.getLastKnownLocation() == null) {
                return false;
            }

            replaceMarker(Point.fromLngLat(point.getLongitude(), point.getLatitude()));
            return true;
        }
    };

    private Callback<DirectionsResponse> buildRoutesCallback = new Callback<DirectionsResponse>() {
        @Override
        public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
            if (response.body() == null || response.body().routes().size() < 1) {
                return;
            }

            currentTargetRoute = response.body().routes().get(0);
            currentAvailableRoutes = response.body().routes();
            drawRoutes(response.body().routes());
            if (updateRoutesListener != null) {
                updateRoutesListener.onRoutesUpdated(currentTargetRoute, currentAvailableRoutes);
            }
        }

        @Override
        public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
            Toast.makeText(mapView.getContext(), throwable.getMessage(), Toast.LENGTH_LONG).show();
            throwable.printStackTrace();
        }
    };

    private void startNavigation() {
        navigation.stopNavigation();
        navigation.startNavigation(currentTargetRoute);
        mapCamera.updateCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_GPS);
    }

    private void stopNavigation() {
        navigation.stopNavigation();
        navigation.startNavigation(currentTargetRoute);
    }

    private OffRouteListener offRouteListener = new OffRouteListener() {
        @Override
        public void userOffRoute(Location location) {
            Point srcPoint = Point.fromLngLat(location.getLongitude(),
                    location.getLatitude());
            getRoutes(srcPoint, currentDestinationPoint, buildRoutesCallback);
        }
//        RouteFetcher
    };
}