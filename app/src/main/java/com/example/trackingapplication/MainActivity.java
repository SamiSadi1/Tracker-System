package com.example.trackingapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import com.example.trackingapplication.data.Database;
import com.example.trackingapplication.data.TrackerDatabase;

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
        Button signOut = findViewById(R.id.signOutBtn);

        greeting.setText("Hello, " + currentUser.name);
        role.setText(currentUser.email + "  •  Employee");

        // Real background tracking needs a foreground service; the switch only
        // reflects whether the tracking screen is currently active.
        trackingSwitch.setChecked(false);
        trackingSwitch.setEnabled(false);

        openLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), LocationActivity.class));
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

    // Kept for compatibility with the previous android:onClick="location" wiring.
    public void location(View view) {
        startActivity(new Intent(getApplicationContext(), LocationActivity.class));
    }
}
