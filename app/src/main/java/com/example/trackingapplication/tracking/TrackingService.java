package com.example.trackingapplication.tracking;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.example.trackingapplication.LocationActivity;
import com.example.trackingapplication.R;
import com.example.trackingapplication.data.Database;
import com.example.trackingapplication.data.Sync;
import com.example.trackingapplication.data.TrackerDatabase;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps recording the user's position while the app is in the background.
 *
 * Runs as a foreground service (Android requires a visible, permanent
 * notification for background location). It keeps going — screen off, app
 * swiped away — until the user presses the notification's Stop action, the
 * "Stop tracking" button in the app, or the service is killed by the system.
 *
 * Every position is written to the on-device SQLite database, so nothing here
 * needs a network.
 */
public class TrackingService extends Service {

    private static final String TAG = "TrackingService";

    public static final String ACTION_START = "com.example.trackingapplication.START_TRACKING";
    public static final String ACTION_STOP = "com.example.trackingapplication.STOP_TRACKING";

    private static final String CHANNEL_ID = "tracking";
    private static final int NOTIFICATION_ID = 1001;

    private static final String PREFS = "tracking_state";
    private static final String KEY_RUNNING = "running";

    /** Same throttle the in-app tracker used: at most one write every 15 s. */
    private static final long MIN_SAVE_INTERVAL_MS = 15_000L;

    private FusedLocationProviderClient fusedClient;
    private LocationRequest locationRequest;
    private TrackerDatabase db;
    private ExecutorService geocodeExecutor;

    private long lastSavedAt = 0L;
    private boolean hasSavedOnce = false;
    private String lastPositionText = "Waiting for a fix…";

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            Location location = locationResult.getLastLocation();
            if (location == null) {
                return;
            }

            lastPositionText = String.format(Locale.US, "%.5f, %.5f",
                    location.getLatitude(), location.getLongitude());
            updateNotification(lastPositionText);

            long now = System.currentTimeMillis();
            if (!hasSavedOnce || now - lastSavedAt >= MIN_SAVE_INTERVAL_MS) {
                hasSavedOnce = true;
                lastSavedAt = now;
                persist(location);
            }
        }
    };

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        db = Database.get(this);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        geocodeExecutor = Executors.newSingleThreadExecutor();

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
                .setMinUpdateIntervalMillis(2000L)
                .build();

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopTracking();
            return START_NOT_STICKY;
        }

        startForegroundNotification();
        setRunning(true);
        startLocationUpdates();

        // START_STICKY: if Android kills the service for memory, it is restarted
        // with a null intent, so tracking resumes until the user really stops it.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopLocationUpdates();
        setRunning(false);
        if (geocodeExecutor != null) {
            geocodeExecutor.shutdown();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ------------------------------------------------------------------
    // Location
    // ------------------------------------------------------------------

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission missing — stopping");
            stopTracking();
            return;
        }
        try {
            fusedClient.requestLocationUpdates(
                    locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission revoked", e);
            stopTracking();
        }
    }

    private void stopLocationUpdates() {
        if (fusedClient != null) {
            fusedClient.removeLocationUpdates(locationCallback);
        }
    }

    private void persist(@NonNull final Location location) {
        final TrackerDatabase.User user = db.currentUser();
        if (user == null) {
            // Nobody signed in: keep the service alive but stop writing orphan rows.
            return;
        }

        final double lat = location.getLatitude();
        final double lng = location.getLongitude();

        geocodeExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String address = resolveAddress(lat, lng);
                // Saves locally and, when Supabase is configured, publishes so other
                // phones (the admin) see this position in near real time.
                Sync.publishLocation(TrackingService.this, user.email, user.name,
                        lat, lng, address);

                String summary = address.isEmpty()
                        ? lastPositionText
                        : lastPositionText + " · " + firstLine(address);
                updateNotification(summary);
            }
        });
    }

    /** Blocking geocoder call — always runs on {@link #geocodeExecutor}. */
    private String resolveAddress(double latitude, double longitude) {
        if (!Geocoder.isPresent()) {
            return "";
        }
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            @SuppressWarnings("deprecation")
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses == null || addresses.isEmpty()) {
                return "";
            }
            Address returnAddress = addresses.get(0);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i <= returnAddress.getMaxAddressLineIndex(); i++) {
                if (i > 0) {
                    builder.append('\n');
                }
                builder.append(returnAddress.getAddressLine(i));
            }
            return builder.toString();
        } catch (IllegalArgumentException | IOException e) {
            return "";
        }
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline == -1 ? text : text.substring(0, newline);
    }

    // ------------------------------------------------------------------
    // Foreground notification
    // ------------------------------------------------------------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.tracking_channel_description));
        channel.setShowBadge(false);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String position) {
        // Tapping the notification brings the user back to the tracking screen.
        PendingIntent openApp = PendingIntent.getActivity(
                this, 0,
                new Intent(this, LocationActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE);

        PendingIntent stop = PendingIntent.getService(
                this, 1,
                new Intent(this, TrackingService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(getString(R.string.tracking_notification_title))
                .setContentText(position)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        position + "\n" + getString(R.string.tracking_notification_body)))
                .setContentIntent(openApp)
                .addAction(0, getString(R.string.stop_tracking), stop)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void startForegroundNotification() {
        Notification notification = buildNotification(lastPositionText);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    /** Refreshes the notification text; safe to call even if notifications are blocked. */
    private void updateNotification(String position) {
        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(position));
            }
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS denied on Android 13+: the service still runs,
            // it just appears only in the system's running-services list.
            Log.w(TAG, "Cannot update notification", e);
        }
    }

    private void stopTracking() {
        setRunning(false);
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ------------------------------------------------------------------
    // State + control from the UI
    // ------------------------------------------------------------------

    private void setRunning(boolean running) {
        prefs(this).edit().putBoolean(KEY_RUNNING, running).apply();
    }

    /** True while the tracking service is alive. */
    public static boolean isRunning(Context context) {
        return prefs(context).getBoolean(KEY_RUNNING, false);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Starts tracking (the caller must already hold location permission). */
    public static void start(Context context) {
        Intent intent = new Intent(context, TrackingService.class).setAction(ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    /** Stops tracking and removes the notification. */
    public static void stop(Context context) {
        Intent intent = new Intent(context, TrackingService.class).setAction(ACTION_STOP);
        try {
            context.startService(intent);
        } catch (Exception e) {
            // Service not running: clear the flag so the UI reflects reality.
            prefs(context).edit().putBoolean(KEY_RUNNING, false).apply();
        }
    }
}
