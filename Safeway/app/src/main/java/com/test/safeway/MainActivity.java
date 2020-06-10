package com.test.safeway;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import com.here.sdk.core.Anchor2D;
import com.here.sdk.core.GeoCircle;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.GeoPolyline;
import com.here.sdk.core.errors.InstantiationErrorException;
import com.here.sdk.mapviewlite.MapCircle;
import com.here.sdk.mapviewlite.MapCircleStyle;
import com.here.sdk.mapviewlite.MapImage;
import com.here.sdk.mapviewlite.MapImageFactory;
import com.here.sdk.mapviewlite.MapMarker;
import com.here.sdk.mapviewlite.MapMarkerImageStyle;
import com.here.sdk.mapviewlite.MapPolyline;
import com.here.sdk.mapviewlite.MapPolylineStyle;
import com.here.sdk.mapviewlite.MapScene;
import com.here.sdk.mapviewlite.MapStyle;
import com.here.sdk.mapviewlite.MapViewLite;
import com.here.sdk.mapviewlite.PixelFormat;
import com.here.sdk.routing.CalculateRouteCallback;
import com.here.sdk.routing.OptimizationMode;
import com.here.sdk.routing.PedestrianOptions;
import com.here.sdk.routing.Route;
import com.here.sdk.routing.RouteOptions;
import com.here.sdk.routing.RouteTextOptions;
import com.here.sdk.routing.RoutingEngine;
import com.here.sdk.routing.RoutingError;
import com.here.sdk.routing.Waypoint;
import com.test.safeway.PermissionsRequestor.ResultListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private PermissionsRequestor permissionsRequestor;
    private PlatformPositioningProvider platformPositioningProvider;
    private DatabaseManager databaseManager;

    private MapViewLite mapView;
    private MapMarker mapMarkerPOI;
    private MapScene mapScene;
    private MapCircle mapCircle;
    private final List<MapPolyline> mapPolylines = new ArrayList<>();

    private RoutingEngine routingEngine;
    private  GeoCoordinates destinationCoordinates;
    private GeoCoordinates currentLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        databaseManager = new DatabaseManager(this);

        // Get a MapViewLite instance from the layout.
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);

        //ask for permissions if required
        handleAndroidPermissions();

        loadMapScene();

        //show user's location
        updateLocation();

        //show points to avoid
        showAll();

        ImageButton searchButton = findViewById(R.id.searchButton);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, Search.class);
                //can not pass GeoCoordinates so we pass the data to rebuild it after
                GeoCoordinates centerMap = mapView.getCamera().getTarget();
                double latitude = centerMap.latitude;
                double longitude = centerMap.longitude;
                intent.putExtra("mapViewLatitude", latitude);
                intent.putExtra("mapViewLongitude", longitude);
                //want result: the destination
                startActivityForResult(intent, 2);// Activity is started with requestCode 2
            }
        });

        ImageButton reportButton = findViewById(R.id.reportButton);
        reportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ReportActivity.class);
                //can not pass GeoCoordinates so we pass the data to rebuild it after
                double latitude = currentLocation.latitude;
                double longitude = currentLocation.longitude;
                intent.putExtra("locationLatitude", latitude);
                intent.putExtra("locationLongitude", longitude);
                startActivity(intent);
                //end the activity because because we don't want to go back but we want to restart it after
                finish();
            }
        });

        ImageButton emergencyButton = findViewById(R.id.emergencyButton);
        emergencyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CallActivity.class);
                startActivity(intent);
            }
        });

    }

    private void handleAndroidPermissions() {
        permissionsRequestor = new PermissionsRequestor(this);
        permissionsRequestor.request(new ResultListener(){

            @Override
            public void permissionsGranted() {

            }

            @Override
            public void permissionsDenied() {
                handleAndroidPermissions();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsRequestor.onRequestPermissionsResult(requestCode, grantResults);
    }

    private void loadMapScene() {
        // Load a scene from the SDK to render the map with a map style.
        mapView.getMapScene().loadScene(MapStyle.NORMAL_DAY, new MapScene.LoadSceneCallback() {
            @Override
            public void onLoadScene(@Nullable MapScene.ErrorCode errorCode) {
                if (errorCode == null) {
                    //set map on Paris
                    mapView.getCamera().setTarget(new GeoCoordinates(48.872156, 2.347464));
                    mapView.getCamera().setZoomLevel(12);

                } else {
                    Log.d(TAG, "onLoadScene failed: " + errorCode.toString());
                }
            }
        });
    }

    private void showLocation(Location location, MapViewLite mapView){
        //remove former circle (when user moves)
        if (mapCircle != null) {
            mapScene.removeMapCircle(mapCircle);
        }

        //create a circle to show user's position
        GeoCircle geoCircle = new GeoCircle(new GeoCoordinates(location.getLatitude(), location.getLongitude()), 30);
        MapCircleStyle mapCircleStyle = new MapCircleStyle();
        mapCircleStyle.setFillColor(0x00908AA0, PixelFormat.RGBA_8888);
        mapCircle = new MapCircle(geoCircle, mapCircleStyle);
        mapScene = mapView.getMapScene();
        mapScene.addMapCircle(mapCircle);
    }

    private void updateLocation(){  //start the location service
        platformPositioningProvider = new PlatformPositioningProvider(MainActivity.this);
        platformPositioningProvider.startLocating(new PlatformPositioningProvider.PlatformLocationListener() {
            @Override
            public void onLocationUpdated(android.location.Location location) {
                //when user moves
                currentLocation = new GeoCoordinates(location.getLatitude(), location.getLongitude());
                showLocation(location, mapView);
                mapView.getCamera().setTarget(currentLocation);
                mapView.getCamera().setZoomLevel(16);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) { //after search Activity
        super.onActivityResult(requestCode, resultCode, data);
        // check if the request code is same as what is passed  here it is 2
        if(requestCode==2){
            Bundle b = data.getExtras();
            if(b!=null){
                destinationCoordinates = new GeoCoordinates( b.getDouble("Latitude"), b.getDouble("Longitude"));
                try {
                    routingEngine = new RoutingEngine();
                    addRoute(destinationCoordinates);
                } catch (InstantiationErrorException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void addRoute(GeoCoordinates destinationCoordinates) {

        for (MapPolyline mapPolyline : mapPolylines) {
            mapView.getMapScene().removeMapPolyline(mapPolyline);
        }
        mapPolylines.clear();

        Waypoint startWaypoint = new Waypoint(currentLocation);
        Waypoint destinationWaypoint = new Waypoint(destinationCoordinates);

        List<Waypoint> waypoints = new ArrayList<>(Arrays.asList(startWaypoint, destinationWaypoint));

        routingEngine.calculateRoute(waypoints, new PedestrianOptions(new RouteOptions(OptimizationMode.FASTEST, 5, null), new RouteTextOptions()), new CalculateRouteCallback() {
                    @Override
                    public void onRouteCalculated(@Nullable RoutingError routingError, @Nullable List<Route> routes) {
                        if (routingError == null && routes != null) {
                            List<List<RiskLocation>> riskLocations = new ArrayList<>();
                            for(int j = 0; j < routes.size(); j++){
                                //compute the number of points to avoid close to each route
                                Route route = routes.get(j);
                                riskLocations.add(new ArrayList<>());
                                safeRoad(route.getPolyline(), riskLocations.get(j));
                            }

                            //compute the safest of all routes
                            int safest = safestRoute(riskLocations);
                            if(safest == 0){
                                //if first route is safest
                                showRouteDetails(routes.get(0));
                                showRouteOnMap(routes.get(0), destinationCoordinates, 0x00908AA0);
                            }
                            else{
                                //show fastest road in black
                                showRouteOnMap(routes.get(0), destinationCoordinates,  255);

                                //show the safest road in green
                                showRouteDetails(routes.get(safest));
                                showRouteOnMap(routes.get(safest), destinationCoordinates, 0x00908AA0);

                                addDestinationMarker();
                            }
                        }
                        else {
                            if (routingError != null) {
                                showDialog("Error while calculating a route:", routingError.toString());
                            }
                        }
                    }
                });
    }

    private void showRouteOnMap(Route route, GeoCoordinates destination, long color) {
        // Show route as polyline.
        GeoPolyline routeGeoPolyline;
        try {
            routeGeoPolyline = new GeoPolyline(route.getPolyline());
        } catch (InstantiationErrorException e) {
            // It should never happen that the route polyline contains less than two vertices.
            return;
        }
        MapPolylineStyle mapPolylineStyle = new MapPolylineStyle();
        mapPolylineStyle.setColor(color, PixelFormat.RGBA_8888);
        mapPolylineStyle.setWidthInPixels(10);
        MapPolyline routeMapPolyline = new MapPolyline(routeGeoPolyline, mapPolylineStyle);
        mapView.getMapScene().addMapPolyline(routeMapPolyline);
        mapPolylines.add(routeMapPolyline);

        databaseManager.getRedPoints(destination);
    }

    private void showRouteDetails(Route route) {
        long estimatedTravelTimeInSeconds = route.getDurationInSeconds();
        int lengthInMeters = route.getLengthInMeters();

        String routeDetails =
                "Travel Time: " + formatTime(estimatedTravelTimeInSeconds)
                        + ", Length: " + formatLength(lengthInMeters);

        showDialog("Route Details", routeDetails);
    }

    private void showDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.show();
    }

    private String formatTime(long sec) {
        long hours = sec / 3600;
        long minutes = (sec % 3600) / 60;

        return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes);
    }

    private String formatLength(int meters) {
        int kilometers = meters / 1000;
        int remainingMeters = meters % 1000;

        return String.format(Locale.getDefault(), "%02d.%02d km", kilometers, remainingMeters);
    }

    private void safeRoad(List<GeoCoordinates> geoCoordinates, List<RiskLocation> IDs){
        for (int i = 0; i < geoCoordinates.size() - 1; i++){
            safeSection(geoCoordinates.get(i), geoCoordinates.get(i + 1), IDs);
        }
    }

    private void safeSection(GeoCoordinates start, GeoCoordinates end, List<RiskLocation> IDs){
        if (start.distanceTo(end) > 150){ //if distance between two points of the road is big
            double latitude = (start.latitude + end.latitude)/2;
            double longitude = (start.longitude + end.longitude)/2;
            GeoCoordinates middle = new GeoCoordinates(latitude, longitude); //take the middle of the two points
            safeSection(start, middle, IDs);
            safeSection(middle, end, IDs);
        }
        else{
            List itemsIDs = databaseManager.getRedPoints(start);    //ask for the points to avoid near (100m) the start of the section
            for (int i = 0; i < itemsIDs.size(); i++){
                RiskLocation itemID = (RiskLocation) itemsIDs.get(i);
                if(!IDs.contains(itemID)){  //two points of section can have the same point to avoid
                    IDs.add(itemID);
                }
            }
        }
    }

    private int safestRoute(List<List<RiskLocation>> riskLocations){
        int shortest = 0;
        for(int i = 0; i < riskLocations.size(); i++){
            if(riskLocations.get(i).size() < riskLocations.get(shortest).size()){
                shortest = i;
            }
        }
        return shortest;
    }

    private void addDestinationMarker() {
        if(mapMarkerPOI != null){
            mapView.getMapScene().removeMapMarker(mapMarkerPOI); //remove if already a marker (a second search for example)
        }

        //load the image
        MapImage mapImage = MapImageFactory.fromResource(MainActivity.this.getResources(), R.drawable.poi);

        mapMarkerPOI = new MapMarker(destinationCoordinates);

        // The bottom, middle position should point to the location.
        // By default, the anchor point is set to 0.5, 0.5.
        MapMarkerImageStyle mapMarkerImageStyle = new MapMarkerImageStyle();
        mapMarkerImageStyle.setAnchorPoint(new Anchor2D(0.5F, 1));

        mapMarkerPOI.addImage(mapImage, mapMarkerImageStyle);

        mapView.getMapScene().addMapMarker(mapMarkerPOI);
    }

    private void showAll(){ //show all points to avoid
        List<RiskLocation> locations = databaseManager.getAll();
        for(int i = 0; i < locations.size(); i++){
            addCircleMapMarker(locations.get(i).getGeoCoordinates());
        }
    }

    private void addCircleMapMarker(GeoCoordinates geoCoordinates) {    //display points to avoid
        //load the image
        MapImage mapImage = MapImageFactory.fromResource(MainActivity.this.getResources(), R.drawable.circle);

        MapMarker mapMarker = new MapMarker(geoCoordinates);
        mapMarker.addImage(mapImage, new MapMarkerImageStyle());

        mapView.getMapScene().addMapMarker(mapMarker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        platformPositioningProvider.stopLocating();
    }

}