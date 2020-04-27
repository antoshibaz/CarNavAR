package com.app.carnavar.maps;

import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.directions.v5.models.LegStep;
import com.mapbox.api.directions.v5.models.RouteLeg;
import com.mapbox.core.constants.Constants;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.utils.PolylineUtils;

import java.util.ArrayList;
import java.util.List;

public class NavMapRoute {

    public static class RouteStepPoint {

    }

    public enum ManeuverType {
        TURN, DEPART, ARRIVE, MERGE, ON_RAMP, OFF_RAMP,
        FORK, ROUND_ABOUT, ROUND_ABOUT_EXIT, END_OF_ROAD,
        NEW_NAME, CONTINUE, ROTARY, ROUND_ABOUT_TURN,
        NOTIFICATION, ROTARY_EXIT, NONE
    }

    private DirectionsRoute directionsRoute;
    private Point[] routePoints;
    private List<LegStep> routeStepPoints = new ArrayList<>();
    private List<LegStep> maneuverPoints = new ArrayList<>();

    public NavMapRoute(DirectionsRoute route) {
        this.directionsRoute = route;
        parseRoute(directionsRoute);
    }

    public Point[] getRoutePoints() {
        return routePoints;
    }

    public List<LegStep> getRouteStepPoints() {
        return routeStepPoints;
    }

    public List<LegStep> getManeuverPoints() {
        return maneuverPoints;
    }

    private void parseRoute(DirectionsRoute route) {
        ArrayList<Point> routePoints = new ArrayList<>();
        List<RouteLeg> legs = route.legs();
        if (legs != null) {
            for (RouteLeg leg : legs) {
                List<LegStep> steps = leg.steps();
                if (steps != null) {
                    for (int i = 0; i < steps.size(); i++) {
                        LegStep step = steps.get(i);
                        if (mapToManeuverType(step.maneuver().type()) != ManeuverType.NONE) {
                            maneuverPoints.add(step);
                        }

                        if (step.geometry() != null) {
                            List<Point> geometryPoints = PolylineUtils.decode(step.geometry(), Constants.PRECISION_6);
                            if (i != steps.size() - 1) {
                                geometryPoints.remove(geometryPoints.size() - 1);
                            }
                            routePoints.addAll(geometryPoints);
                        }

                        routeStepPoints.add(step);
                    }
                }
            }
        }

        this.routePoints = routePoints.toArray(new Point[0]);
    }

    public static ManeuverType mapToManeuverType(String maneuver) {
        if (maneuver == null) {
            return ManeuverType.NONE;
        }
        switch (maneuver) {
            case "turn":
                return ManeuverType.TURN;
            case "depart":
                return ManeuverType.DEPART;
            case "arrive":
                return ManeuverType.ARRIVE;
            case "merge":
                return ManeuverType.MERGE;
            case "on ramp":
                return ManeuverType.ON_RAMP;
            case "off ramp":
                return ManeuverType.OFF_RAMP;
            case "fork":
                return ManeuverType.FORK;
            case "roundabout":
                return ManeuverType.ROUND_ABOUT;
            case "exit roundabout":
                return ManeuverType.ROUND_ABOUT_EXIT;
            case "end of road":
                return ManeuverType.END_OF_ROAD;
            case "new name":
                return ManeuverType.NEW_NAME;
            case "continue":
                return ManeuverType.CONTINUE;
            case "rotary":
                return ManeuverType.ROTARY;
            case "roundabout turn":
                return ManeuverType.ROUND_ABOUT_TURN;
            case "notification":
                return ManeuverType.NOTIFICATION;
            case "exit rotary":
                return ManeuverType.ROTARY_EXIT;
            default:
                return ManeuverType.NONE;
        }
    }
}
