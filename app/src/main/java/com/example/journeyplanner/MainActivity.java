package com.example.journeyplanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.library.BuildConfig;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;

import static com.google.gson.JsonParser.parseString;
import static java.lang.Double.parseDouble;
import static java.lang.Thread.sleep;
import static org.osmdroid.tileprovider.util.StorageUtils.getStorage;


public class MainActivity extends AppCompatActivity implements LocationListener {

    MapView mv;

    double latitude;
    double longitude;

    ItemizedIconOverlay<OverlayItem> items;
    ItemizedIconOverlay.OnItemGestureListener<OverlayItem> markerGestureListener;

    // global string to hold info on buses times
    ArrayList<String> busesArray = new ArrayList<>();

    PopupWindow popupWindow;

    //create hashmaps to store operator and directions
    Map<String, String> operator_map = new HashMap<>();
    Map<String, String> direction_map = new HashMap<>();

    //setup poly line for route mapping
    //TODO:reset these when a button or something is pressed?
    Polyline polyline = new Polyline();
    ArrayList<GeoPoint> pathPoints = new ArrayList<>();

    Polyline journeyPolyline = new Polyline();
    ArrayList<GeoPoint> journeyPoints = new ArrayList<>();

    String lonlat = "";

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("MissingPermission")
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

//        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
//
//        StrictMode.setThreadPolicy(policy);

        //request location access
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        // This line sets the user agent, a requirement to download OSM maps
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        setContentView(R.layout.activity_main);


        IConfigurationProvider provider = Configuration.getInstance();
        provider.setUserAgentValue(BuildConfig.APPLICATION_ID);
        provider.setOsmdroidBasePath(getStorage());
        provider.setOsmdroidTileCache(getStorage());

        //create tap labels for markers
        markerGestureListener = new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
            public boolean onItemLongPress(int i, OverlayItem item) {
                //create popup for all info on that stop
                onButtonShowPopupWindowClick(item);
                return true;
            }

            public boolean onItemSingleTapUp(int i, OverlayItem item) {
                //create popup for all info on that stop
                onButtonShowPopupWindowClick(item);

                return true;
            }
        };

        //create empty list of items that will get added to the map overlay
        items = new ItemizedIconOverlay<OverlayItem>(getApplicationContext(), new ArrayList<OverlayItem>(), markerGestureListener);


        //initialise map + settings
        mv = (MapView) findViewById(R.id.map1);
        //TODO: figure out why the fuck this shit dont work to refresh map
        mv.invalidate();
        mv.setMultiTouchControls(true);
        mv.getController().setZoom(15);

        //setup location grabbing
        LocationManager mgr = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        final Location location = mgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        mv.getController().setCenter(new GeoPoint(latitude, longitude));


        ImageButton useMyLoc = findViewById(R.id.useMyLoc);
        final EditText startEdit = (EditText) findViewById(R.id.startEdit);
        final EditText endEdit = (EditText) findViewById(R.id.endEdit);

        // when the use location button is pressed, set the text to be "current location"
        useMyLoc.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String myLoc = "Current location";
                startEdit.setText(myLoc);
            }
        });

        ImageButton useMyLocEnd = findViewById(R.id.useMyLocEnd);
        // when the use location button is pressed, set the text to be "current location"
        useMyLocEnd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String myLoc = "Current location";
                endEdit.setText(myLoc);
            }
        });

        Button clearRoute = findViewById(R.id.clearRoute);
        // when the clear route button is pressed, remove the route
        clearRoute.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!pathPoints.isEmpty()) {
                    //remove route line
                    mv.getOverlays().remove(polyline);
                    mv.getOverlays().remove(items);
                    pathPoints.clear();
                    polyline.setPoints(pathPoints);
                    mv.invalidate();
                }
                if (!journeyPoints.isEmpty()) {
                    //remove journey route line
                    mv.getOverlays().remove(journeyPolyline);
                    mv.getOverlays().remove(items);
                    journeyPoints.clear();
                    journeyPolyline.setPoints(journeyPoints);
                    mv.invalidate();
                }
            }
        });

        ImageButton centreMap = findViewById(R.id.centreBtn);

        // when the centre button is pressed, centre the map
        //TODO: some stupid permission check if location isn't on
        centreMap.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();

                //set map centre back to current location
                mv.getController().setCenter(new GeoPoint(latitude, longitude));
            }
        });

        //setup search button
        Button button = (Button) findViewById(R.id.searchBtn);

        //add event listener to search button
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String start = startEdit.getText().toString();
                String end = endEdit.getText().toString();

                //display based on missing info
                //start is empty but end is not
                if (start.equals("") && !end.equals("")) {
                    Toast.makeText(getApplicationContext(), "Please enter a starting location!", Toast.LENGTH_LONG).show();
                }
                //start is not empty but end is
                else if (!start.equals("") && end.equals("")) {
                    Toast.makeText(getApplicationContext(), "Please enter a destination!", Toast.LENGTH_LONG).show();
                }
                //both start and end are empty
                else if (start.equals("") && end.equals("")) {
                    Toast.makeText(getApplicationContext(), "Please enter a starting location and destination!", Toast.LENGTH_LONG).show();
                }
                //neither start nor end are empty
                else if (start.equals("Current location") && end.equals("Current location")) {
                    Toast.makeText(getApplicationContext(), "You are trying to path to yourself!", Toast.LENGTH_LONG).show();
                } else {
                    mv.getOverlays().add(viewJourney(start, end));
                }

            }
        });

        //setup refresh button
        Button refresh_button = (Button) findViewById(R.id.refreshBtn);
        refresh_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mv.getOverlays().size() != 0) {
                    items.removeAllItems();
                    mv.getOverlays().clear();
                }
                getNearbyStops();
            }
        });

        // My Location Overlay
        MyLocationNewOverlay myLocationoverlay = new MyLocationNewOverlay(mv);
        myLocationoverlay.enableMyLocation(); // not on by default
        mv.getOverlays().add(myLocationoverlay);
    }

    public void onLocationChanged(Location newLoc) {

    }

    public void onProviderDisabled(String provider) {

    }

    public void onProviderEnabled(String provider) {

    }

    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    public void getNearbyStops() {
        String url;
        //update lat and lon values to map centre values (so you can view other stops not nearby gps location)
        latitude = mv.getMapCenter().getLatitude();
        longitude = mv.getMapCenter().getLongitude();
        try {
            //open connection to transport api
            url = "https://transportapi.com/v3/uk/places.json?app_id=68970837&" +
                    "app_key=8006564a12df3043f69377d72854436e&lat=" + latitude +
                    "&lon=" + longitude + "&type=bus_stop";

            RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
            JsonObjectRequest stopsArray = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        JSONArray jsonArray = response.getJSONArray("member");

                        //loop through each json member returned
                        for (int i = 0; i < jsonArray.length(); i++) {

                            JSONObject oneStop = jsonArray.getJSONObject(i);

                            //Pulling items from the array
                            String type = oneStop.getString("type");
                            String name = oneStop.getString("name");
                            double lat = oneStop.getDouble("latitude");
                            double lon = oneStop.getDouble("longitude");
                            String atco_code = oneStop.getString("atcocode");

                            OverlayItem newItem = new OverlayItem(name, type + "," + atco_code, new GeoPoint(lat, lon));

                            items.addItem(i, newItem);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }, new Response.ErrorListener() { //Create an error listener to handle errors appropriately.
                @Override
                public void onErrorResponse(VolleyError error) {
                    //This code is executed if there is an error.
                    System.out.println(error.getMessage());
                }
            });
            requestQueue.add(stopsArray);

            //if map is empty then add all markers to the overlay
            if (mv.getOverlays().size() == 0) {
                mv.getOverlays().add(items);
                mv.invalidate();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // specific timetable for single bus stop
    public String[] getTimes(final String atco_code) {
        String url;
        try {
            //open connection to transport api
            url = "https://transportapi.com/v3/uk/bus/stop/" + atco_code +
                    "/live.json?app_id=68970837&app_key=8006564a12df3043f69377d72854436e&group=no&nextbuses=yes";

            System.out.println(url);

            //busesArray.clear();

            RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
            JsonObjectRequest busArray = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        JSONObject jsonObj = response.getJSONObject("departures");
                        JSONArray allBus = jsonObj.getJSONArray("all");
                        //check if there are any buses running
                        if (allBus.length() != 0) {
                            //loop through each json departure returned
                            for (int i = 0; i < allBus.length(); i++) {

                                JSONObject oneBus = allBus.getJSONObject(i);
                                //Pulling items from the array
                                String line_name = oneBus.getString("line");

                                String operator_name = oneBus.getString("operator_name").replace(",", ";");
                                String best_departure_estimate = oneBus.getString("best_departure_estimate");
                                String operator_code = oneBus.getString("operator");
                                String dir = oneBus.getString("dir");

                                //only use relevant info
                                busesArray.add("Departure: " + best_departure_estimate + ", Line: " + line_name + ", "
                                        + "Operator: " + operator_name);

                                System.out.println(i + ", " + line_name + ", " + operator_name + ", " + best_departure_estimate + ", " + operator_code + ", " + dir + ", " + atco_code);

                                //map the name to the code for reference
                                if (!operator_map.containsKey(operator_name)) {
                                    operator_map.put(operator_name, operator_code);
                                }
                                //map the atco code to the direction for reference
                                if (!direction_map.containsKey(line_name + "," + atco_code)) {
                                    direction_map.put(line_name + "," + atco_code, dir);
                                }
                            }
                        } else {
                            busesArray.clear();
                            busesArray.add("No buses are currently running!");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() { //Create an error listener to handle errors appropriately.
                @Override
                public void onErrorResponse(VolleyError error) {
                    //This code is executed if there is an error.
                    System.out.println(error.getMessage());
                }
            });
            requestQueue.add(busArray);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String[] buses = busesArray.toArray(new String[0]);
        //clear buses times variable when exiting popup
        busesArray.clear();
        return buses;
    }

    public void onButtonShowPopupWindowClick(final OverlayItem item) {

        // inflate the layout of the popup window
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_window, null);

        // create the popup window
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        popupWindow = new PopupWindow(popupView, width, height, false);

        // show the popup window
        popupWindow.showAtLocation(mv, Gravity.CENTER, 0, 0);

        // allow editing of text in popup
        final ListView popup_list = popupView.findViewById(R.id.popup_list);

        //split snippet into type and atco code
        String[] split = item.getSnippet().split(",");

        //create a list of all the data gathered
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, getTimes(split[1]));
        popup_list.setAdapter(adapter);

        //make separate text for general stop info
        TextView popup_text = popupView.findViewById(R.id.popup_text);
        String new_text = "Stop name: " + item.getTitle()
                + "\nType: " + split[0] + "\n\nTimetable\n";

        popup_text.setText(new_text);

        // dismiss the popup window when button is pressed
        Button closeBtn = popupView.findViewById(R.id.closeBtn);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                popupWindow.dismiss();
            }
        });


        // List item click listener
        popup_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {

                // item value (all the bus info)
                String itemValue = (String) popup_list.getItemAtPosition(position);
                //clear map before plotting route
                items.removeAllItems();
                mv.getOverlays().clear();
                mv.invalidate();

                //plot route then close popup
                mv.getOverlays().add(viewRoute(itemValue, item));

                popupWindow.dismiss();

            }
        });
    }


    //view route from pressing an item in the list
    public Polyline viewRoute(String line_info, OverlayItem item) {
        String url;
        try {
            //split the info into relevant sections
            String atco_code = item.getSnippet().split(",")[1];
            String line = line_info.split(",")[1].split(": ")[1];
            String operator_name = line_info.split(",")[2].split(": ")[1];

            //retrieve mapped values from hash maps
            String operator_code = operator_map.get(operator_name);
            String direction = direction_map.get(line + "," + atco_code);

            //open connection to transport api
            url = "https://transportapi.com/v3/uk/bus/route/" + operator_code + "/" + line + "/" +
                    direction + "/" + atco_code + "/timetable.json?app_id=68970837&app_key=8006564a12df3043f69377d72854436e&edge_geometry=true&stops=all";

            System.out.println(url);

            polyline.setColor(R.color.colorPrimary);
            polyline.setWidth(15);

            RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
            JsonObjectRequest routeArray = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        JSONArray jsonObj = response.getJSONArray("stops");
                        //loop through each json stop returned
                        for (int i = 0; i < jsonObj.length(); i++) {
                            JSONObject oneStop = jsonObj.getJSONObject(i);

                            //Pulling items from the array
                            String type = "bus_stop";
                            String name = oneStop.getString("stop_name");
                            double lat = oneStop.getDouble("latitude");
                            double lon = oneStop.getDouble("longitude");
                            String atco_code = oneStop.getString("atcocode");
                            //String bearing = oneStop.getString("bearing");

                            OverlayItem newItem = new OverlayItem(name, type + "," + atco_code, new GeoPoint(lat, lon));

                            items.addItem(i, newItem);
                            if (i < (jsonObj.length() - 1)) {
                                JSONObject next = oneStop.getJSONObject("next");
                                for (int j = 0; j < next.length(); j++) {
                                    JSONArray coords = next.getJSONArray("coordinates");
                                    //loop through each route coordinate
                                    for (int k = 0; k < coords.length(); k++) {
                                        JSONArray coordinates = coords.getJSONArray(k);
                                        String[] latAndLon = coordinates.toString().split(",");
                                        //retrieve lat and lon from object
                                        double nextLon = parseDouble(latAndLon[0].replace("[", ""));
                                        double nextLat = parseDouble(latAndLon[1].replace("]", ""));
                                        //add to a new geo point
                                        GeoPoint nextCoord = new GeoPoint(nextLat, nextLon);
                                        pathPoints.add(nextCoord);
                                    }
                                }
                            }
                            //add route line to map
                            polyline.setPoints(pathPoints);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() { //Create an error listener to handle errors appropriately.
                @Override
                public void onErrorResponse(VolleyError error) {
                    //This code is executed if there is an error.
                    System.out.println(error.getMessage());
                }
            });
            requestQueue.add(routeArray);
            //add route markers to map
            mv.getOverlays().add(items);
            //refresh map
            mv.invalidate();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return polyline;
    }

    //display a journey using the locations input
    public Polyline viewJourney(final String startLoc, final String endLoc) {
        String town = "";
        if (!startLoc.equals("Current location") || !endLoc.equals("Current location")) {
            //if start location is the user's location
            if (startLoc.equals("Current location")) {
                town = endLoc;
            }
            //if end location is the user's location
            else if (endLoc.equals("Current location")) {
                town = startLoc;
            }
        }

        //connect to transport api
        final OkHttpClient client = new OkHttpClient();
        final okhttp3.Request request = new okhttp3.Request.Builder().url("https://transportapi.com/v3/uk/places.json?app_id=68970837&" +
                "app_key=8006564a12df3043f69377d72854436e&lat=" + latitude + "&lon=" + longitude
                + "&query=" + town +"&type=settlement").build();

        lonlat = "";
        Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    okhttp3.Response response = client.newCall(request).execute();
                    JsonElement jsonElement = parseString(response.body().string());
                    JsonElement jsonArray = jsonElement.getAsJsonObject();

                    JsonObject jsonObject = jsonArray.getAsJsonObject();
                    JsonArray member = jsonObject.get("member").getAsJsonArray();


                    JsonArray towns_response = member.getAsJsonArray();

                    System.out.println(towns_response);

                    //only grab the first object in the array (closest match to user input)
                    JsonObject town_info = towns_response.get(0).getAsJsonObject();

                    //Pulling info items about town
                    double townLat = town_info.get("latitude").getAsDouble();
                    double townLon = town_info.get("longitude").getAsDouble();
                    System.out.println(townLon + "   ;     " + townLat);
                    lonlat = townLon + "," + townLat;
                    System.out.println("6 : " + lonlat);

                    //if start and destination are not current location
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        };
        new Thread(runnable).start();

        //temp variable
        final String finalTown = town;

        Runnable runnable1 = new Runnable(){

            @Override
            public void run() {
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                new LongOperation().execute(lonlat, startLoc, endLoc, finalTown);
            }
        };
        new Thread(runnable1).start();
        mv.invalidate();

        return journeyPolyline;
    }

    public class LongOperation extends AsyncTask<String, Void, Polyline> {

        @Override
        protected Polyline doInBackground(String... params) {

            String lonlat = params[0];
            //reset each time
            String url = "";
            String town = params[3];

            //retrieve start value from params
            String start = params[1];


            //check whether the search was for start or end location
            if ( town.equals(start)){
                url = "https://transportapi.com/v3/uk/public/journey/from/lonlat:" + lonlat + "/to/lonlat:"
                        + longitude + "," + latitude + ".json?app_id=68970837&app_key=8006564a12df3043f69377d72854436e&not_modes=tube-boat";
            }
            else {
                url  = "https://transportapi.com/v3/uk/public/journey/from/lonlat:" + longitude + "," + latitude + "/to/lonlat:"  + lonlat +
                        ".json?app_id=68970837&app_key=8006564a12df3043f69377d72854436e&not_modes=tube-boat";
            }

            System.out.println(url);

            //open connection to transport api
            RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
            JsonObjectRequest routeArray = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                @Override
                public void onResponse(JSONObject response) {

                    try {
                        JSONArray jsonObj = response.getJSONArray("routes");
                        //loop through each json stop returned

                        JSONObject jsonObject = jsonObj.getJSONObject(0);

                        //Pulling items from the array
                        String duration = jsonObject.getString("duration");
                        //set path settings
                        journeyPolyline.setColor(R.color.colorPrimary);
                        journeyPolyline.setWidth(15);

                        //loops through each section of route
                        JSONArray route_parts = jsonObject.getJSONArray("route_parts");

                        for (int j = 0; j < route_parts.length(); j++) {
                            //individual route section (journey stop)
                            JSONObject journey_stop = route_parts.getJSONObject(j);
                            //get relevant part info
                            String mode = journey_stop.getString("mode");

                            //TODO: implement info appearing on route click (times + durations)
                            String part_duration = journey_stop.getString("duration");
                            String part_departure = journey_stop.getString("departure_time");

                            String line_name = journey_stop.getString("line_name");

                            JSONArray coordinates = journey_stop.getJSONArray("coordinates");
                            //loop through each route coordinate
                            for (int k = 0; k < coordinates.length(); k++) {
                                JSONArray specific_coord = coordinates.getJSONArray(k);
                                //split the retrieved lat and lon into individual variables
                                String[] latAndLon = specific_coord.toString().split(",");
                                //retrieve lat and lon from object
                                double nextLon = parseDouble(latAndLon[0].replace("[", ""));
                                double nextLat = parseDouble(latAndLon[1].replace("]", ""));
                                //add to a new geo point
                                GeoPoint nextCoord = new GeoPoint(nextLat, nextLon);
                                journeyPoints.add(nextCoord);
                            }
                            //add route line to map
                            journeyPolyline.setPoints(journeyPoints);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() { //Create an error listener to handle errors appropriately.
                @Override
                public void onErrorResponse(VolleyError error) {
                    //This code is executed if there is an error.
                    System.out.println(error.getMessage());
                }
            });
            routeArray.setRetryPolicy(new DefaultRetryPolicy(
                    10000,
                    1,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            routeArray.setShouldCache(false);
            requestQueue.add(routeArray);
            //add route markers to map
            mv.getOverlays().add(items);
            //clear journey points array
            journeyPoints.clear();
            //refresh map
            mv.invalidate();
            return journeyPolyline;
        }

        @Override
        protected void onPostExecute(Polyline result) {
            super.onPostExecute(result);
        }
    }
}