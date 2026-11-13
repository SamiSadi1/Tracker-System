package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.widget.TextView;

import com.example.trackingapplication.data.Database;
import com.example.trackingapplication.data.TrackerDatabase;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tracks the signed-in user and stores the position in the on-device database
 * (no Firebase). A new fix is written immediately and then at most every 15 s.
 */
public class LocationActivity extends AppCompatActivity {

    private static final int LOCATION_REQUEST_CODE = 10001;
    private static final int LOCATION_SETTINGS_REQUEST_CODE = 1001;

    /** Don't hit the disk on every single GPS tick. */
    private static final long MIN_SAVE_INTERVAL_MS = 15_000L;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private TrackerDatabase db;
    private TextView statusText;

    private long lastSavedAt = 0L;
    private boolean hasSavedOnce = false;

    /** Geocoding touches the network/disk, so keep it off the main thread. */
    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            Location location = locationResult.getLastLocation();
            if (location == null) {
                return;
            }

            showPosition(location);

            long now = System.currentTimeMillis();
            if (!hasSavedOnce || now - lastSavedAt >= MIN_SAVE_INTERVAL_MS) {
                hasSavedOnce = true;
                lastSavedAt = now;
                saveLocation(location);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        statusText = findViewById(R.id.statusText);
        db = Database.get(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
                .setMinUpdateIntervalMillis(2000L)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (hasLocationPermission()) {
            checkSettingsAndStartLocationUpdates();
        } else {
            askLocationPermission();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        geocodeExecutor.shutdown();
    }

    // ------------------------------------------------------------------
    // Permissions / settings
    // ------------------------------------------------------------------

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void askLocationPermission() {
        if (hasLocationPermission()) {
            return;
        }
        // Android 12+ needs fine AND coarse to be requested together.
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkSettingsAndStartLocationUpdates();
            } else {
                setStatus("Location permission denied. Tracking cannot start.");
            }
        }
    }

    private void checkSettingsAndStartLocationUpdates() {
        LocationSettingsRequest request = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build();
        SettingsClient client = LocationServices.getSettingsClient(this);

        Task<LocationSettingsResponse> task = client.checkLocationSettings(request);

        task.addOnSuccessListener(new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                startLocationUpdates();
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    ResolvableApiException apiException = (ResolvableApiException) e;
                    try {
                        apiException.startResolutionForResult(
                                LocationActivity.this, LOCATION_SETTINGS_REQUEST_CODE);
                    } catch (IntentSender.SendIntentException ex) {
                        setStatus("Could not open location settings: " + ex.getMessage());
                    }
                } else {
                    setStatus("Location is not available on this device: " + e.getMessage());
                }
            }
        });
    }

    private void startLocationUpdates() {
        if (!hasLocationPermission()) {
            return;
        }
        setStatus("Tracking… waiting for a GPS fix.");
        fusedLocationProviderClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        if (fusedLocationProviderClient != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    private void saveLocation(@NonNull final Location location) {
        final TrackerDatabase.User user = db.currentUser();
        if (user == null) {
            setStatus("You are signed out — nothing to save.");
            return;
        }

        final double lat = location.getLatitude();
        final double lng = location.getLongitude();

        geocodeExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String address = resolveAddress(lat, lng);
                db.saveLocation(user.email, lat, lng, address);

                setStatus(String.format(Locale.US,
                        "Saved %s\n%.5f, %.5f\n%s",
                        user.email, lat, lng,
                        address.isEmpty() ? "(no address)" : address));
            }
        });
    }

    /** Blocking geocoder call — always runs on {@link #geocodeExecutor}. */
    private String resolveAddress(double latitude, double longitude) {
        if (!Geocoder.isPresent()) {
            return "";
        }
        Geocoder geocoder = new Geocoder(LocationActivity.this, Locale.getDefault());
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
            // No geocoder backend, or network unavailable — coordinates are still saved.
            return "";
        }
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------

    private void showPosition(@NonNull Location location) {
        setStatus(String.format(Locale.US, "Current position\n%.5f, %.5f",
                location.getLatitude(), location.getLongitude()));
    }

    private void setStatus(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusText != null) {
                    statusText.setText(message);
                }
            }
        });
    }
}
