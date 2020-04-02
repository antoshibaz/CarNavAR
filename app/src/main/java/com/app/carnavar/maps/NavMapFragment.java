package com.app.carnavar.maps;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.app.carnavar.ArActivity;
import com.app.carnavar.R;
import com.app.carnavar.ui.NavMapBottomSheet;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonObject;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class NavMapFragment extends Fragment {

    public static final String TAG = "NavMapFragment";

    private static NavMapFragment navMapFragment;

    private MapView mapView;
    private NavMap navMap;
    private NavMapBottomSheet bottomSheet;
    private FloatingActionButton userLocationBtn;
    private FloatingActionButton placeSelectedBtn;
    private FloatingActionButton locationsSearchFab;
    private FloatingActionButton fabArNav;

    public NavMapFragment() {
    }

    public static NavMapFragment getInstance() {
        if (navMapFragment == null) {
            navMapFragment = new NavMapFragment();
        }
        return navMapFragment;
    }

    public NavMap getNavMap() {
        return navMap;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Mapbox.getInstance(getActivity().getApplicationContext(), getString(R.string.mapbox_access_token));
        return inflater.inflate(R.layout.fragment_navmap, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapView = view.findViewById(R.id.navMapView);
        mapView.onCreate(savedInstanceState);
        navMap = new NavMap(mapView, getString(R.string.mapbox_map_style_streets));

        bottomSheet = view.findViewById(R.id.mapbox_plugins_picker_bottom_sheet);
        bottomSheet.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View view, int i) {
                if (i == BottomSheetBehavior.STATE_COLLAPSED) {
                    placeSelectedBtn.animate().scaleX(1).scaleY(1).setDuration(50).start();
                    fabArNav.animate().scaleX(1).scaleY(1).setDuration(50).start();
                } else if (i == BottomSheetBehavior.STATE_HIDDEN) {
                    placeSelectedBtn.animate().scaleX(0).scaleY(0).setDuration(50).start();
                    fabArNav.animate().scaleX(0).scaleY(0).setDuration(50).start();
                }
            }

            @Override
            public void onSlide(@NonNull View view, float v) {
            }
        });

        locationsSearchFab = view.findViewById(R.id.fab_location_search);
        locationsSearchFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new PlaceAutocomplete.IntentBuilder()
                        .accessToken(getString(R.string.mapbox_access_token))
                        .placeOptions(PlaceOptions.builder()
                                .backgroundColor(Color.parseColor("#EEEEEE"))
                                .limit(5)
                                .build(PlaceOptions.MODE_CARDS))
                        .build(getActivity());
                startActivityForResult(intent, 0);
                //overridePendingTransition(R.anim.slide_down_top, R.anim.slide_down_top);
            }
        });

        userLocationBtn = view.findViewById(R.id.fab_mylocation);
        userLocationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        placeSelectedBtn = view.findViewById(R.id.fab_place_chosen);
        placeSelectedBtn.setOnClickListener(view12 -> {
        });

        fabArNav = view.findViewById(R.id.fab_arnav);
        fabArNav.setOnClickListener(view1 -> {
            Point dstPoint = navMap.getCurrentDestinationPoint();
            if (dstPoint != null) {
                Intent intent = new Intent(getActivity(), ArActivity.class);
                intent.putExtra("destination_marker", new double[]{dstPoint.longitude(),
                        dstPoint.latitude()});
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Poi place is not selected", Toast.LENGTH_LONG).show();
            }
        });
    }

    public void showSelectedPlaceDetails(CarmenFeature carmenFeature) {
        if (carmenFeature == null) {
            carmenFeature = CarmenFeature.builder().placeName(
                    String.format(Locale.US, "[%f, %f]",
                            navMap.getCurrentDestinationPoint().latitude(),
                            navMap.getCurrentDestinationPoint().latitude())
            ).text("No address found").properties(new JsonObject()).build();
        }
        bottomSheet.setPlaceDetails(carmenFeature);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == 0) {
            CarmenFeature selectedCarmenFeature = PlaceAutocomplete.getPlace(data);
            navMap.replaceMarker(selectedCarmenFeature);
            navMap.moveCamera((Point) selectedCarmenFeature.geometry());
            showSelectedPlaceDetails(selectedCarmenFeature);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        mapView.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mapView.onDestroy();
        navMap.shutdown();
    }
}