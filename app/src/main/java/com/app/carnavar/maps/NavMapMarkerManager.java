package com.app.carnavar.maps;

import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.plugins.annotation.Symbol;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions;

import java.util.ArrayList;
import java.util.List;

public class NavMapMarkerManager {

    public static final String NAVMAP_MARKER_NAME = "navmap-marker";
    private final List<Symbol> mapMarkersSymbols = new ArrayList<>();
    private final SymbolManager symbolManager;

    public NavMapMarkerManager(SymbolManager symbolManager) {
        this.symbolManager = symbolManager;
        symbolManager.setIconAllowOverlap(true);
        symbolManager.setIconIgnorePlacement(true);
    }

    public void addMarkerFor(Point position) {
        SymbolOptions options = createDefaultMarkerOptionsFor(position);
        createMarkerFrom(options);
    }

    public void addCustomMarkerFor(SymbolOptions options) {
        createMarkerFrom(options);
    }

    public void removeAllMarkerSymbols() {
        for (Symbol markerSymbol : mapMarkersSymbols) {
            symbolManager.delete(markerSymbol);
        }
        mapMarkersSymbols.clear();
    }

    private SymbolOptions createDefaultMarkerOptionsFor(Point position) {
        LatLng markerPosition = new LatLng(position.latitude(),
                position.longitude());
        return new SymbolOptions()
                .withLatLng(markerPosition)
                .withIconImage(NAVMAP_MARKER_NAME)
                .withIconOffset(new Float[] {0f, -16f});
    }

    private void createMarkerFrom(SymbolOptions options) {
        Symbol symbol = symbolManager.create(options);
        mapMarkersSymbols.add(symbol);
    }
}
