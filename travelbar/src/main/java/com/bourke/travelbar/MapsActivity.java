package com.bourke.travelbar;

import android.app.ActionBar;
import android.app.LoaderManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentSender;
import android.content.Loader;
import android.database.Cursor;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.SearchView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "TravelBar/MapsActivity";

    // Define a request code to send to Google Play services. Returned in Activity.onActivityResult
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    // Started to query ContentProvider for all places matching a string
    private final static int LOADER_SEARCH = 0;

    // Started to query ContentProvider for details on a specific place
    private final static int LOADER_DETAILS = 1;

    private GoogleMap mMap;

    // TODO: persist
    private List<Marker> mMarkers = new ArrayList<Marker>();

    private LocationClient mLocationClient;
    private LocationRequest mLocationRequest;

    private Marker mDestinationMarker;
    private LatLng mDestination;

    private MenuItem mMenuStart;
    private MenuItem mMenuStop;

    private boolean mInitialCenterDone = false;

    @Override public boolean onMarkerClick(Marker marker) {
        for (Marker m : mMarkers) {
            m.setIcon(BitmapDescriptorFactory.defaultMarker());
        }

        marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
        mDestination = marker.getPosition();

        return false;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override public Loader<Cursor> onCreateLoader(int arg0, Bundle query) {
        CursorLoader cLoader = null;

        if (arg0 == LOADER_SEARCH) {
            cLoader = new CursorLoader(getBaseContext(), PlaceProvider.SEARCH_URI, null, null,
                    new String[]{query.getString("query")}, null);
        } else if (arg0 == LOADER_DETAILS) {
            cLoader = new CursorLoader(getBaseContext(), PlaceProvider.DETAILS_URI, null, null,
                    new String[]{query.getString("query")}, null);
        }

        return cLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> arg0, Cursor c) {
        showLocations(c);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        // Not used
    }

    @Override public void onConnected(Bundle bundle) {
        // TODO: they recommend wrapping a boolean here to disable updates if user requests
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    @Override public void onDisconnected() {
        Toast.makeText(this, "Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();
    }

    @Override public void onConnectionFailed(ConnectionResult connectionResult) {
        setProgressBarIndeterminateVisibility(false);

        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(
                        this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                * Thrown if Google Play services canceled the original
                * PendingIntent
                */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            Toast.makeText(this, "Connection Failure", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onLocationChanged(Location location) {
        // Center on users location on startup, but don't continue to recenter the camera on every
        // subsequent location update
        if (!mInitialCenterDone) {
            setProgressBarIndeterminateVisibility(false);

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
            mInitialCenterDone = true;
        }
    }

    @Override public void onMapClick(LatLng latLng) {
        for (Marker m : mMarkers) {
            m.remove();
        }
        mMarkers.clear();

        // TODO: persist for rotate and other state change
        Marker marker = mMap.addMarker(new MarkerOptions().position(latLng));
        marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
        mDestinationMarker = marker;
        mDestination = latLng;
        mMarkers.add(marker);

        // If the service is already started, update it with an event
        DestinationChangedEvent event = new DestinationChangedEvent(latLng.latitude,
                latLng.longitude);
        BusProvider.getInstance().post(event);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();

        mLocationClient = new LocationClient(this, this, this);

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(Constants.UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(Constants.FASTEST_INTERVAL);

        initActionBar();

        handleIntent(getIntent());

        // TODO: check if play services available:
        // https://developer.android.com/training/location/receive-location-updates.html
    }

    @Override protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /*
     * Called when the Activity becomes visible.
     */
    @Override protected void onStart() {
        super.onStart();
        // Connect the client.
        setProgressBarIndeterminateVisibility(true);
        mLocationClient.connect();
    }

    /*
     * Called when the Activity is no longer visible.
     */
    @Override protected void onStop() {
        if (mLocationClient.isConnected()) {
            mLocationClient.removeLocationUpdates(this);
        }

        // Disconnecting the client invalidates it.
        mLocationClient.disconnect();

        super.onStop();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);

        mMenuStart = menu.findItem(R.id.action_start);
        mMenuStop = menu.findItem(R.id.action_stop);
        mMenuStop.setVisible(false);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

        return super.onCreateOptionsMenu(menu);
    }

    @Override public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_start:
                if (mLocationClient.isConnected()) {
                    if (mDestination != null) {
                        startProgressBarService();
                        mMenuStart.setVisible(false);
                        mMenuStop.setVisible(true);
                    } else {
                        Toast.makeText(this, "First tap the map to set a location",
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.action_stop:
                stopService(new Intent(this, ProgressBarService.class));
                mMenuStop.setVisible(false);
                mMenuStart.setVisible(true);
                break;
        }

        return super.onMenuItemSelected(featureId, item);
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.setMyLocationEnabled(true);
        mMap.setOnMapClickListener(this);
        mMap.setOnMarkerClickListener(this);
    }

    private void startProgressBarService() {
        Location currentLocation = mLocationClient.getLastLocation();

        Intent intent = new Intent(this, ProgressBarService.class);
        intent.putExtra(ProgressBarService.STARTING_LAT, currentLocation.getLatitude());
        intent.putExtra(ProgressBarService.STARTING_LON, currentLocation.getLongitude());

        intent.putExtra(ProgressBarService.DESTINATION_LAT, mDestination.latitude);
        intent.putExtra(ProgressBarService.DESTINATION_LON, mDestination.longitude);

        startService(intent);
    }

    private void initActionBar() {
        SpannableString s = new SpannableString(getString(R.string.app_name));
        s.setSpan(new TypefaceSpan(this, "Bender-Solid.otf"), 0, s.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Update the action bar title with the TypefaceSpan instance
        ActionBar actionBar = getActionBar();
        actionBar.setTitle(s);

        // Set the icon
        actionBar.setIcon(R.drawable.ic_actionbar);
    }

    private void handleIntent(Intent intent){
        if (intent.getAction().equals(Intent.ACTION_SEARCH)){
            doSearch(intent.getStringExtra(SearchManager.QUERY));
        } else if(intent.getAction().equals(Intent.ACTION_VIEW)){
            getPlace(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
        }
    }

    private void doSearch(String query) {
        Bundle data = new Bundle();
        data.putString("query", query);
        getLoaderManager().restartLoader(LOADER_SEARCH, data, this);
    }

    private void getPlace(String query){
        Bundle data = new Bundle();
        data.putString("query", query);
        getLoaderManager().restartLoader(LOADER_DETAILS, data, this);
    }

    private void showLocations(Cursor c) {
        LatLng position = null;

        mMap.clear();
        mMarkers.clear();

        while (c.moveToNext()) {
            MarkerOptions markerOptions = new MarkerOptions();
            position = new LatLng(Double.parseDouble(c.getString(1)),
                    Double.parseDouble(c.getString(2)));
            markerOptions.position(position);
            markerOptions.title(c.getString(0));
            Marker marker = mMap.addMarker(markerOptions);
            mMarkers.add(marker);
        }
        if (position != null) {
            CameraUpdate cameraPosition = CameraUpdateFactory.newLatLng(position);
            mMap.animateCamera(cameraPosition);
        }
    }
}
