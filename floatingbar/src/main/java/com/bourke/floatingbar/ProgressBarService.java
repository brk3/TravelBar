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

    private Location mStartingPoint = new Location(TAG);
    private Location mDestination = new Location(TAG);
    private float mTotalDistance;

    @Override public IBinder onBind(Intent intent) {
        // Not used
        return null;
    }

    @Override public void onCreate() {
        super.onCreate();

        mLocationClient = new LocationClient(this, this, this);
        mLocationClient.connect();

        // TODO: set from UI
        mStartingPoint.setLatitude(53.345905);
        mStartingPoint.setLongitude(-6.294289);
        mDestination.setLatitude(53.347799);
        mDestination.setLongitude(-6.243922);
        mTotalDistance = mStartingPoint.distanceTo(mDestination);

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();
        // Use high accuracy
        mLocationRequest.setPriority(
                LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 5 seconds
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        // Set the fastest update interval to 1 second
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mProgressBar = new ProgressBar(getBaseContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        mProgressBar.getProgressDrawable().setColorFilter(HOLO_GREEN, PorterDuff.Mode.SRC_IN);

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

        mProgressBar.setProgress(mProgressStatus);

        windowManager.addView(mProgressBar, params);
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
        Location currentLocation = mLocationClient.getLastLocation();
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

        Log.d(TAG, location.toString());
        Log.d(TAG, "Distance remaining (meters): " + distanceRemaining);
        Log.d(TAG, "Distance complete (percentage): " + mProgressStatus);
    }
}