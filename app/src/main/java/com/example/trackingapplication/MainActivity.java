package com.example.trackingapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.trackingapplication.data.Database;
import com.example.trackingapplication.data.TrackerDatabase;
import com.example.trackingapplication.tracking.TrackingService;

/** Employee home. Backed by the on-device database (no Firebase). */
public class MainActivity extends AppCompatActivity {

    private TrackerDatabase.User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TrackerDatabase db = Database.get(this);
        currentUser = db.currentUser();

        if (currentUser == null) {
            startActivity(new Intent(getApplicationContext(), LoginActivity.class));
            finish();
            return;
        }

        TextView greeting = findViewById(R.id.greeting);
        TextView role = findViewById(R.id.roleText);
        Switch trackingSwitch = findViewById(R.id.trackingSwitch);
        Button openLocation = findViewById(R.id.openLocationBtn);
        Button messages = findViewById(R.id.messagesBtn);
        Button signOut = findViewById(R.id.signOutBtn);

        greeting.setText("Hello, " + currentUser.name);
        role.setText(currentUser.email + "  •  Employee");

        // The switch reflects (and controls) the background tracking service.
        trackingSwitch.setChecked(TrackingService.isRunning(this));
        trackingSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                        // Ask on the tracking screen, which handles the result.
                        buttonView.setChecked(false);
                        startActivity(new Intent(getApplicationContext(), LocationActivity.class));
                        return;
                    }
                    TrackingService.start(MainActivity.this);
                } else {
                    TrackingService.stop(MainActivity.this);
                }
            }
        });

        openLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), LocationActivity.class));
            }
        });

        int unread = db.totalUnread(currentUser.email);
        messages.setText(unread > 0
                ? "Messages (" + unread + " new)"
                : getString(R.string.open_messages));

        messages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), MessagesActivity.class));
            }
        });

        signOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                db.signOut();
                startActivity(new Intent(getApplicationContext(), LoginActivity.class));
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Keep the switch truthful after a stop from the notification.
        android.widget.Switch trackingSwitch = findViewById(R.id.trackingSwitch);
        if (trackingSwitch != null) {
            boolean running = TrackingService.isRunning(this);
            if (trackingSwitch.isChecked() != running) {
                trackingSwitch.setOnCheckedChangeListener(null);
                trackingSwitch.setChecked(running);
                trackingSwitch.setOnCheckedChangeListener(trackingListener);
            }
        }
    }

    private final android.widget.CompoundButton.OnCheckedChangeListener trackingListener =
            new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton buttonView,
                                             boolean isChecked) {
                    if (isChecked) {
                        TrackingService.start(MainActivity.this);
                    } else {
                        TrackingService.stop(MainActivity.this);
                    }
                }
            };

    // Kept for compatibility with the previous android:onClick="location" wiring.
    public void location(View view) {
        startActivity(new Intent(getApplicationContext(), LocationActivity.class));
    }
}
