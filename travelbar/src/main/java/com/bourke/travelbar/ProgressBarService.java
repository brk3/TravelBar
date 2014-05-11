package com.bourke.travelbar;

import android.animation.ObjectAnimator;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
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

    private static final String TAG = "TravelBar/ProgressBarService";

    private WindowManager windowManager;
    private ProgressBar mProgressBar;

    private LocationClient mLocationClient;
    private LocationRequest mLocationRequest;

    private Location mStartingPoint;
    private Location mDestination;

    private float mTotalDistance;

    private BroadcastReceiver mStopReceiver;

    public static boolean RUNNING = false;

    public static final String STARTING_LAT =
            "com.bourke.travelbar.ProgressBarService.STARTING_LAT";
    public static final String STARTING_LON =
            "com.bourke.travelbar.ProgressBarService.STARTING_LON";

    public static final String DESTINATION_LAT =
            "com.bourke.travelbar.ProgressBarService.DESTINATION_LAT";
    public static final String DESTINATION_LON =
            "com.bourke.travelbar.ProgressBarService.DESTINATION_LON";

    public static final int NOTIFICATION_ID = 0;

    @Override public IBinder onBind(Intent intent) {
        // Not used
        return null;
    }

    @Override public void onCreate() {
        super.onCreate();

        RUNNING = true;

        BusProvider.getInstance().register(this);

        mLocationClient = new LocationClient(this, this, this);
        mLocationClient.connect();

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(Constants.UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(Constants.FASTEST_INTERVAL);

        mProgressBar = new ProgressBar(getBaseContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        mProgressBar.getProgressDrawable().setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN);

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

        showRunningNotification("Initialising " + getString(R.string.app_name));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Parse starting point
        double startingLat = intent.getDoubleExtra(STARTING_LAT, -1);
        double startingLon = intent.getDoubleExtra(STARTING_LON, -1);

        if (startingLat != -1 || startingLon != -1) {
            mStartingPoint = new Location(TAG);
            mStartingPoint.setLatitude(startingLat);
            mStartingPoint.setLongitude(startingLon);

            if (BuildConfig.DEBUG) {
                Log.d(TAG, String.format("Received starting location: %f, %f", startingLat,
                        startingLon));
            }
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

            if (BuildConfig.DEBUG) {
                Log.d(TAG, String.format("Received destination: %f, %f", destinationLat,
                        destinationLon));
            }
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

        if (mProgressBar != null) {
            windowManager.removeView(mProgressBar);
        }

        if (mStopReceiver != null) {
            try {
                unregisterReceiver(mStopReceiver);
            } catch (IllegalArgumentException e) {
                // http://stackoverflow.com/a/3568906/663370
            }
        }

        // Clear any notifications we've shown
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancelAll();

        RUNNING = false;
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
        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format("Destination changed to: %f,%f", event.lat, event.lon));
        }

        mDestination.setLatitude(event.lat);
        mDestination.setLongitude(event.lon);

        mTotalDistance = mStartingPoint.distanceTo(mDestination);
    }

    private void updateProgress(Location currentLocation) {
        mTotalDistance = mStartingPoint.distanceTo(mDestination);

        float distanceRemaining = currentLocation.distanceTo(mDestination);

        int progressStatus = (int) ((distanceRemaining / mTotalDistance) * 100);
        progressStatus = Math.abs(progressStatus - 100);

        // Set a min > 0 to make the bar more visible when empty
        if (progressStatus <= 3) {
            progressStatus = 3;
        }

        // Update the progress bar smoothly using ObjectAnimator
        ObjectAnimator animation = ObjectAnimator.ofInt(mProgressBar, "progress", progressStatus);
        animation.setDuration(500); // 0.5 second
        animation.setInterpolator(new LinearInterpolator());
        animation.start();

        if (progressStatus >= 90) {
            showArrivalNotification();
            mLocationClient.disconnect();
        } else if (progressStatus >= 80) {
            mProgressBar.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        } else if (progressStatus >= 60) {
            mProgressBar.getProgressDrawable().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
        } else {
            mProgressBar.getProgressDrawable().setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN);
        }

        if (progressStatus <= 90) {
            // Update the ongoing notification
            showRunningNotification(progressStatus + "% journey completed");
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Start: " + mStartingPoint);
            Log.d(TAG, "Destination: " + mDestination);
            Log.d(TAG, "Total distance (metres): " + mTotalDistance);
            Log.d(TAG, "Distance remaining (meters): " + distanceRemaining);
            Log.d(TAG, "Distance complete (percentage): " + progressStatus);
        }
    }

    private void popArrivalNotification() {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_notify)
                        .setContentTitle("Yeehaw!")
                        .setContentText("You are arriving at your destination")
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_ALL);

        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                unregisterReceiver(this);
                stopSelf();
            }
        }, new IntentFilter("com.bourke.ProgressBarService.STOP_SERVICE"));

        PendingIntent contentIntent = PendingIntent.getBroadcast(this, 0,
                new Intent("com.bourke.ProgressBarService.STOP_SERVICE"),
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private PendingIntent getStopServicePendingIntent() {
        PendingIntent stopIntent = PendingIntent.getBroadcast(this, 0,
                new Intent("com.bourke.ProgressBarService.STOP_SERVICE"),
                PendingIntent.FLAG_UPDATE_CURRENT);

        mStopReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                unregisterReceiver(this);
                stopSelf();
            }
        };
        registerReceiver(mStopReceiver,
                new IntentFilter("com.bourke.ProgressBarService.STOP_SERVICE"));

        return stopIntent;
    }

    private void showRunningNotification(String titleText) {
        PendingIntent peStopService = getStopServicePendingIntent();

        PendingIntent peMapsActivity = PendingIntent.getActivity(this, 0,
                new Intent(this, MapsActivity.class), 0);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_notify)
                        .setContentTitle(titleText)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .addAction(R.drawable.ic_action_stop_light,
                                getString(R.string.stop_tracking), peStopService)
                        .setContentIntent(peMapsActivity);

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showArrivalNotification() {
        PendingIntent peStopService = getStopServicePendingIntent();

        PendingIntent peMapsActivity = PendingIntent.getActivity(this, 0,
                new Intent(this, MapsActivity.class), 0);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_notify)
                        .setTicker("Yeehaw!")  // TODO: could have multiple strings here
                        .setContentTitle("You are arriving at your destination!")
                        .setAutoCancel(true)
                        .setVibrate(new long[]{2000, 2000})
                        .setDefaults(Notification.DEFAULT_ALL)
                        .addAction(R.drawable.ic_action_stop_light,
                                getString(R.string.stop_tracking), peStopService)
                        .setContentIntent(peMapsActivity);

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}