package com.bourke.floatingbar;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.squareup.otto.Subscribe;

public class ProgressBarService extends Service implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "FloatingBar/ProgressBarService";

    // Update frequency in milliseconds
    private static final long UPDATE_INTERVAL = 3000;
    // A fast frequency ceiling in milliseconds
    private static final long FASTEST_INTERVAL = 1000;

    private WindowManager windowManager;
    private ProgressBar mProgressBar;

    private int mProgressStatus = 0;

    private final int HOLO_GREEN = Color.rgb(153, 204, 0);

    private LocationClient mLocationClient;
    private LocationRequest mLocationRequest;

    private Location mStartingPoint;
    private Location mDestination;

    private float mTotalDistance;

    public static final String STARTING_LAT =
            "com.bourke.floatingbar.ProgressBarService.STARTING_LAT";
    public static final String STARTING_LON =
            "com.bourke.floatingbar.ProgressBarService.STARTING_LON";

    public static final String DESTINATION_LAT =
            "com.bourke.floatingbar.ProgressBarService.DESTINATION_LAT";
    public static final String DESTINATION_LON =
            "com.bourke.floatingbar.ProgressBarService.DESTINATION_LON";

    @Override public IBinder onBind(Intent intent) {
        // Not used
        return null;
    }

    @Override public void onCreate() {
        super.onCreate();

        BusProvider.getInstance().register(this);

        mLocationClient = new LocationClient(this, this, this);
        mLocationClient.connect();

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

        mProgressBar = new ProgressBar(getBaseContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        mProgressBar.getProgressDrawable().setColorFilter(HOLO_GREEN, PorterDuff.Mode.SRC_IN);
        mProgressBar.setProgress(mProgressStatus);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.x = 0;
        params.y = 0;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(mProgressBar, params);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Parse starting point
        double startingLat = intent.getDoubleExtra(STARTING_LAT, -1);
        double startingLon = intent.getDoubleExtra(STARTING_LON, -1);

        if (startingLat != -1 || startingLon != -1) {
            mStartingPoint = new Location(TAG);
            mStartingPoint.setLatitude(startingLat);
            mStartingPoint.setLongitude(startingLon);

            Log.d(TAG, String.format("Received starting location: %f, %f", startingLat,
                    startingLon));
        } else {
            throw new IllegalStateException("Starting point required to start ProgressBarService");
        }

        // Parse destination
        double destinationLat = intent.getDoubleExtra(DESTINATION_LAT, -1);
        double destinationLon = intent.getDoubleExtra(DESTINATION_LON, -1);

        if (destinationLat != -1 && destinationLon != -1) {
            mDestination = new Location(TAG);
            mDestination.setLatitude(destinationLat);
            mDestination.setLongitude(destinationLon);

            Log.d(TAG, String.format("Received destination: %f, %f", destinationLat,
                    destinationLon));
        } else {
            throw new IllegalStateException("Destination required to start ProgressBarService");
        }

        mTotalDistance = mStartingPoint.distanceTo(mDestination);

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override public void onDestroy() {
        super.onDestroy();

        if (mLocationClient.isConnected()) {
            mLocationClient.removeLocationUpdates(this);
        }

        // Disconnecting the client invalidates it.
        mLocationClient.disconnect();

        if (mProgressBar != null) windowManager.removeView(mProgressBar);
    }

    @Override public void onConnected(Bundle bundle) {
        // TODO: they recommend wrapping a boolean here to disable updates if user requests
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    @Override public void onDisconnected() {
        Toast.makeText(this, "Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();
    }

    @Override public void onConnectionFailed(ConnectionResult connectionResult) {
        Toast.makeText(this, "Connection Failure", Toast.LENGTH_SHORT).show();
    }

    @Override public void onLocationChanged(Location location) {
        updateProgress(location);
    }

    @Subscribe public void destinationChanged(DestinationChangedEvent event) {
        Log.d(TAG, String.format("Destination changed to: %f,%f", event.lat, event.lon));

        mDestination.setLatitude(event.lat);
        mDestination.setLongitude(event.lon);

        mTotalDistance = mStartingPoint.distanceTo(mDestination);
    }

    private void updateProgress(Location currentLocation) {
        mTotalDistance = mStartingPoint.distanceTo(mDestination);

        float distanceRemaining = currentLocation.distanceTo(mDestination);

        mProgressStatus = (int) ((distanceRemaining / mTotalDistance) * 100);
        mProgressStatus = Math.abs(mProgressStatus - 100);
        mProgressBar.setProgress(mProgressStatus);

        if (mProgressStatus >= 80) {
            mProgressBar.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        } else if (mProgressStatus >= 60) {
            mProgressBar.getProgressDrawable().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
        } else {
            mProgressBar.getProgressDrawable().setColorFilter(HOLO_GREEN, PorterDuff.Mode.SRC_IN);
        }

        Log.d(TAG, "Start: " + mStartingPoint);
        Log.d(TAG, "Destination: " + mDestination);
        Log.d(TAG, "Total distance (metres): " + mTotalDistance);
        Log.d(TAG, "Distance remaining (meters): " + distanceRemaining);
        Log.d(TAG, "Distance complete (percentage): " + mProgressStatus);
    }
}