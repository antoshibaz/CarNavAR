package com.app.carnavar;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.app.carnavar.maps.NavMapFragment;
import com.app.carnavar.services.GpsImuService;
import com.app.carnavar.services.ServicesRepository;
import com.app.carnavar.utils.android.PermissionsManager;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";

    private PermissionsManager permissionsManager;

    private NavMapFragment navMapFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PermissionsManager.checkPermissions(getApplicationContext(), AppConfigs.APP_PERMISSIONS)) {
            if (permissionsManager == null) {
                permissionsManager = new PermissionsManager(permissionsListener);
            }
            permissionsManager.requestPermissions(this, AppConfigs.APP_PERMISSIONS);
        } else {
            // start app
            startApp(savedInstanceState);
        }
    }

    private void startApp(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            navMapFragment = NavMapFragment.getInstance();

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_map_container, navMapFragment, NavMapFragment.TAG)
                    .commit();
        } else {
            navMapFragment = (NavMapFragment) getSupportFragmentManager()
                    .findFragmentByTag(NavMapFragment.TAG);
            //navMapFragment = NavMapFragment.getInstance();
        }

        ServicesRepository.getInstance().startService(getApplicationContext(), GpsImuService.class);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ServicesRepository.getInstance().stopService(getApplicationContext(), GpsImuService.class);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == 0) {
            CarmenFeature selectedCarmenFeature = PlaceAutocomplete.getPlace(data);
            navMapFragment.getNavMap().replaceMarker(selectedCarmenFeature);
            navMapFragment.getNavMap().moveCamera((Point) selectedCarmenFeature.geometry());
            navMapFragment.showSelectedPlaceDetails(selectedCarmenFeature);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private PermissionsManager.PermissionsListener permissionsListener = new PermissionsManager.PermissionsListener() {
        @Override
        public void onExplanationNeeded(List<String> permissionsToExplain) {
            // explain permission for user
        }

        @Override
        public void onPermissionResult(boolean granted) {
            if (!granted) {
                Toast.makeText(getApplicationContext(), "Permissions is denied", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                // start app
                startApp(null);
            }
        }
    };
}