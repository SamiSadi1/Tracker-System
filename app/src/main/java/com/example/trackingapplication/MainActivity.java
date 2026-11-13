package com.example.trackingapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Wired up in code instead of android:onClick so a rename can't break it at runtime.
        TextView location = findViewById(R.id.Location);
        location.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), LocationActivity.class));
            }
        });

        Button signOut = findViewById(R.id.signOutBtn);
        signOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
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
