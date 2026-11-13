package com.example.trackingapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.trackingapplication.data.Database;
import com.example.trackingapplication.data.TrackerDatabase;
import com.example.trackingapplication.tracking.TrackingService;

import java.text.DateFormat;
import java.util.Date;

/**
 * Start/stop screen for background tracking.
 *
 * The actual recording lives in {@link TrackingService} (a foreground service),
 * so it keeps running after this screen — or the whole app — is closed, until
 * the user presses "Stop tracking" here or in the notification.
 */
public class LocationActivity extends AppCompatActivity {

    private static final int LOCATION_REQUEST_CODE = 10001;
    private static final int NOTIFICATION_REQUEST_CODE = 10002;

    private TrackerDatabase db;
    private TextView statusText;
    private TextView savedText;
    private Button toggleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        db = Database.get(this);
        statusText = findViewById(R.id.statusText);
        savedText = findViewById(R.id.savedText);
        toggleButton = findViewById(R.id.toggleTrackingBtn);

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TrackingService.isRunning(LocationActivity.this)) {
                    TrackingService.stop(LocationActivity.this);
                    refresh();
                    Toast.makeText(LocationActivity.this,
                            R.string.tracking_stopped, Toast.LENGTH_SHORT).show();
                } else {
                    startTracking();
                }
            }
        });

        Button back = findViewById(R.id.backBtn);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        refresh();
    }

    // ------------------------------------------------------------------
    // Start / stop
    // ------------------------------------------------------------------

    private void startTracking() {
        if (!hasLocationPermission()) {
            askForPermissions();
            return;
        }
        TrackingService.start(this);
        refresh();
        Toast.makeText(this, R.string.tracking_running, Toast.LENGTH_LONG).show();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void askForPermissions() {
        // Location (fine+coarse together, as Android 12+ requires). Notification
        // permission is only relevant on Android 13+ and is asked in the same pass.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    LOCATION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startTracking();
            } else {
                statusText.setText("Location permission denied. Tracking cannot start.");
            }
        }
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------

    private void refresh() {
        boolean running = TrackingService.isRunning(this);

        toggleButton.setText(running
                ? R.string.stop_background_tracking
                : R.string.start_background_tracking);

        statusText.setText(running
                ? R.string.tracking_running
                : R.string.tracking_stopped);

        TrackerDatabase.User me = db.currentUser();
        if (me == null) {
            savedText.setText("You are signed out — nothing will be saved.");
            return;
        }

        TrackerDatabase.UserLocation location = db.locationFor(me.email);
        if (location == null || !location.hasLocation()) {
            savedText.setText(R.string.no_location_yet);
            return;
        }

        String when = location.updatedAt != null
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(location.updatedAt))
                : "unknown";

        StringBuilder saved = new StringBuilder();
        saved.append(String.format(java.util.Locale.US, "Last saved: %.5f, %.5f",
                location.latitude, location.longitude));
        if (location.address != null && !location.address.trim().isEmpty()) {
            saved.append('\n').append(location.address);
        }
        saved.append("\nUpdated ").append(when);

        savedText.setText(saved.toString());
    }
}
